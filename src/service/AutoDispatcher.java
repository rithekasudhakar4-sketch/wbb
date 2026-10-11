package service;

import algorithm.RoutePlanner;
import dao.AmbulanceDao;
import dao.EmergencyDao;
import dao.HospitalDao;
import dao.TripDao;
import db.Database;
import model.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Autonomous Emergency Dispatch Engine for Nalam Nagar.
 *
 * Runs on a background daemon scheduler to automatically allocate ambulances
 * and hospital beds to triaged emergencies in strict priority order (Critical > Serious > Minor).
 *
 * Features:
 *  - Event-driven immediate triggers + 2.5s recurring safety tick.
 *  - Dijkstra fastest-route ambulance selection respecting live road delays/closures.
 *  - Specialty matching with graceful fallback to nearest hospital with available beds.
 *  - Preemption: Reassigns an ambulance heading to a Minor call if a Critical call arrives.
 *  - Thread-safe synchronization with HTTP endpoints.
 *  - Simulated vehicle trip progression (EN_ROUTE_PATIENT -> EN_ROUTE_HOSPITAL -> COMPLETED).
 */
public class AutoDispatcher {

    private final DispatchManager dispatchManager;
    private final AmbulanceManager ambulanceManager;
    private final HospitalManager hospitalManager;
    private final RoutePlanner routePlanner;

    private final AmbulanceDao ambulanceDao;
    private final HospitalDao hospitalDao;
    private final EmergencyDao emergencyDao;
    private final TripDao tripDao;

    private final Object dispatchLock;
    private final ScheduledExecutorService scheduler;

    // Configuration flags
    private volatile boolean autoDispatchEnabled = true;
    private volatile boolean preemptionEnabled = true;

    // Simulation durations in seconds (speeded up for real-time visualization)
    private static final long SIM_LEG1_SECONDS = 4; // Amb -> Patient
    private static final long SIM_LEG2_SECONDS = 4; // Patient -> Hospital

    // Active scheduled futures per ambulance for clean cancellation / preemption
    private final Map<String, List<ScheduledFuture<?>>> activeSimulations = new ConcurrentHashMap<>();
    private String lastWaitingLogId = null;

    public AutoDispatcher(DispatchManager dispatchManager,
                          AmbulanceManager ambulanceManager,
                          HospitalManager hospitalManager,
                          RoutePlanner routePlanner,
                          AmbulanceDao ambulanceDao,
                          HospitalDao hospitalDao,
                          EmergencyDao emergencyDao,
                          TripDao tripDao,
                          Object dispatchLock) {
        this.dispatchManager = dispatchManager;
        this.ambulanceManager = ambulanceManager;
        this.hospitalManager = hospitalManager;
        this.routePlanner = routePlanner;
        this.ambulanceDao = ambulanceDao;
        this.hospitalDao = hospitalDao;
        this.emergencyDao = emergencyDao;
        this.tripDao = tripDao;
        this.dispatchLock = dispatchLock;
        this.scheduler = Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "AutoDispatcher-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the recurring background evaluation loop.
     */
    public void start() {
        // Runs every 2.5 seconds as a periodic safety net
        scheduler.scheduleWithFixedDelay(this::safeCycle, 2, 2, TimeUnit.SECONDS);
        System.out.println("[AutoDispatcher] Background automated dispatch engine started.");
    }

    /**
     * Stops the scheduler and active simulations.
     */
    public void stop() {
        scheduler.shutdownNow();
    }

    /**
     * Wakes up the dispatcher immediately when system state changes:
     * - new emergency call intake
     * - ambulance freed after trip completion
     * - road opened/closed or delay updated
     * - hospital beds modified
     */
    public void trigger() {
        if (autoDispatchEnabled) {
            scheduler.submit(this::safeCycle);
        }
    }

    private void safeCycle() {
        try {
            runDispatchCycle();
        } catch (Exception e) {
            System.err.println("[AutoDispatcher] Error in cycle: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Core dispatch loop. Evaluates pending requests and assigns resources
     * until either the queue is empty or no vehicles/beds are available.
     */
    public void runDispatchCycle() {
        synchronized (dispatchLock) {
            if (!autoDispatchEnabled) {
                return;
            }

            // Loop while there are waiting emergencies in the PriorityQueue
            while (dispatchManager.hasPendingRequests()) {
                EmergencyRequest request = dispatchManager.peekNextRequest();
                if (request == null) break;

                Location patientLoc = request.getEmergencyLocation();
                Capability requiredCap = request.getRequiredCapability() != null
                        ? request.getRequiredCapability()
                        : Capability.GENERAL_WARD;

                // -------------------------------------------------------------
                // 1. Ambulance Allocation (with optional Minor-case preemption)
                // -------------------------------------------------------------
                AmbulanceManager.AmbulanceSelectionResult ambResult =
                        ambulanceManager.findFastestAmbulance(patientLoc, routePlanner);

                Ambulance chosenAmbulance = null;
                RouteResult ambulanceToPatientRoute = null;

                if (ambResult.isSuccess()) {
                    chosenAmbulance = ambResult.getAmbulance();
                    ambulanceToPatientRoute = ambResult.getRoute();
                } else {
                    // Fleet busy or blocked. Check for preemption if top case is CRITICAL
                    if (preemptionEnabled && request.getSeverity() == Severity.CRITICAL) {
                        chosenAmbulance = findPreemptableAmbulance(patientLoc);
                        if (chosenAmbulance != null) {
                            ambulanceToPatientRoute = routePlanner.planRoute(chosenAmbulance.getCurrentLocation(), patientLoc);
                        }
                    }

                    if (chosenAmbulance == null || ambulanceToPatientRoute == null || !ambulanceToPatientRoute.isReachable()) {
                        if (!request.getRequestId().equals(lastWaitingLogId)) {
                            System.out.printf("[AUTO] Waiting for resources: All ambulances are busy. Emergency #%s (%s, Zone: %s) queued at priority rank 1.%n",
                                    request.getRequestId(), request.getSeverity(), patientLoc.getName());
                            lastWaitingLogId = request.getRequestId();
                        }
                        break; // Stop cycle; retry when an ambulance completes a trip
                    }
                }

                // -------------------------------------------------------------
                // 2. Hospital Matching (with specialty, or fallback with beds)
                // -------------------------------------------------------------
                HospitalManager.HospitalSelectionResult hospResult =
                        hospitalManager.evaluateAndSelectHospital(patientLoc, requiredCap, routePlanner);

                Hospital chosenHospital = null;
                RouteResult patientToHospitalRoute = null;

                if (hospResult.isSuccess()) {
                    chosenHospital = hospResult.getHospital();
                    patientToHospitalRoute = hospResult.getRoute();
                } else {
                    // Fallback: check if ANY hospital has available beds
                    HospitalManager.HospitalSelectionResult fallbackResult =
                            hospitalManager.findNearestHospitalWithBeds(patientLoc, routePlanner);

                    if (fallbackResult.isSuccess()) {
                        chosenHospital = fallbackResult.getHospital();
                        patientToHospitalRoute = fallbackResult.getRoute();
                        System.out.printf("[AUTO] Fallback: No hospital with specialty %s has beds. Reassigned to: %s (Available Beds: %d)%n",
                                requiredCap.name(), chosenHospital.getName(), chosenHospital.getAvailableBeds());
                    } else {
                        // Emergency Surge Protocol: When all hospital beds in the city are full, route to nearest reachable facility
                        HospitalManager.HospitalSelectionResult surgeResult =
                                hospitalManager.findNearestHospital(patientLoc, routePlanner);
                        if (surgeResult.isSuccess()) {
                            chosenHospital = surgeResult.getHospital();
                            patientToHospitalRoute = surgeResult.getRoute();
                            System.out.printf("[AUTO] Surge Protocol: All city beds occupied. Emergency #%s routed to %s under emergency surge.%n",
                                    request.getRequestId(), chosenHospital.getName());
                        } else {
                            if (!request.getRequestId().equals(lastWaitingLogId)) {
                                System.out.printf("[AUTO] Waiting for resources: No hospital reachable due to road closures. Emergency #%s held in queue.%n",
                                        request.getRequestId());
                                lastWaitingLogId = request.getRequestId();
                            }
                            break; // Stop cycle; retry next tick or when roads clear
                        }
                    }
                }

                lastWaitingLogId = null;

                // -------------------------------------------------------------
                // 3. Execute Dispatch & Commit Database Transaction
                // -------------------------------------------------------------
                dispatchManager.pollNextRequest(); // Remove from PriorityQueue

                double ambToPatientTime = ambulanceToPatientRoute.getTotalTime();
                double patientToHospTime = patientToHospitalRoute.getTotalTime();
                double totalLookaheadETA = ambToPatientTime + patientToHospTime;

                // In-memory state updates
                chosenHospital.admitPatient();
                chosenAmbulance.setStatus(AmbulanceStatus.EN_ROUTE_PATIENT);
                chosenAmbulance.setTargetLocation(patientLoc);
                chosenAmbulance.setAssignedHospital(chosenHospital);
                chosenAmbulance.setAssignedRequest(request);
                chosenAmbulance.setEtaMinutes(ambToPatientTime);

                request.setAssignedAmbulance(chosenAmbulance);
                request.setAssignedHospital(chosenHospital);
                request.setStatus("DISPATCHED");

                // Transactional Database Update
                persistDispatchTransaction(chosenAmbulance, chosenHospital, request);

                // Required console output log
                System.out.printf("[AUTO] Emergency #%s (%s, Zone %s) -> Ambulance %s -> Hospital %s, ETA %.1f min.%n",
                        request.getRequestId(),
                        request.getSeverity(),
                        patientLoc.getName(),
                        chosenAmbulance.getAmbulanceId(),
                        chosenHospital.getName(),
                        totalLookaheadETA);

                // -------------------------------------------------------------
                // 4. Start Simulated Trip Progress
                // -------------------------------------------------------------
                startTripSimulation(chosenAmbulance, request, chosenHospital, ambToPatientTime, patientToHospTime);
            }
        }
    }

    /**
     * Looks for an ambulance currently heading to a MINOR call that hasn't reached its patient yet,
     * and reassigns it to the Critical emergency.
     */
    private Ambulance findPreemptableAmbulance(Location criticalPatientLoc) {
        Ambulance bestCandidate = null;
        double minTime = Double.MAX_VALUE;

        for (Ambulance amb : ambulanceManager.getAllAmbulances()) {
            if (amb.getStatus() == AmbulanceStatus.EN_ROUTE_PATIENT && amb.getAssignedRequest() != null) {
                if (amb.getAssignedRequest().getSeverity() == Severity.MINOR) {
                    RouteResult route = routePlanner.planRoute(amb.getCurrentLocation(), criticalPatientLoc);
                    if (route.isReachable() && route.getTotalTime() < minTime) {
                        minTime = route.getTotalTime();
                        bestCandidate = amb;
                    }
                }
            }
        }

        if (bestCandidate != null) {
            EmergencyRequest preemptedReq = bestCandidate.getAssignedRequest();
            Hospital preemptedHosp = bestCandidate.getAssignedHospital();

            // Cancel any ongoing trip simulation for this ambulance
            cancelSimulation(bestCandidate.getAmbulanceId());

            // Release the hospital bed that was reserved for the minor request
            if (preemptedHosp != null) {
                preemptedHosp.releaseBed();
                hospitalDao.releaseBed(preemptedHosp.getId());
            }

            // Return minor request back to PriorityQueue
            preemptedReq.setStatus("PENDING");
            preemptedReq.setAssignedAmbulance(null);
            preemptedReq.setAssignedHospital(null);
            dispatchManager.addEmergencyRequest(preemptedReq);
            emergencyDao.updateStatus(preemptedReq.getRequestId(), "PENDING", null, null);

            System.out.printf("[AUTO] Preemption: Critical emergency preempted Ambulance %s from Minor request #%s! Minor request returned to queue.%n",
                    bestCandidate.getAmbulanceId(), preemptedReq.getRequestId());
        }

        return bestCandidate;
    }

    /**
     * Simulates live ambulance travel:
     * Stage 1: En route to patient (SIM_LEG1_SECONDS)
     * Stage 2: En route to hospital (SIM_LEG2_SECONDS)
     * Stage 3: Trip completion & admission, triggering next auto-dispatch
     */
    private void startTripSimulation(Ambulance amb, EmergencyRequest req, Hospital hosp, double ambToPatientTime, double patientToHospTime) {
        String ambId = amb.getAmbulanceId();
        cancelSimulation(ambId);

        List<ScheduledFuture<?>> futures = new ArrayList<>();

        // Stage 1: Arrival at patient -> Change status to EN_ROUTE_HOSPITAL
        ScheduledFuture<?> leg1Future = scheduler.schedule(() -> {
            synchronized (dispatchLock) {
                if (amb.getAssignedRequest() == req) {
                    amb.setCurrentLocation(req.getEmergencyLocation());
                    amb.setTargetLocation(hosp.getLocation());
                    amb.setStatus(AmbulanceStatus.EN_ROUTE_HOSPITAL);
                    amb.setEtaMinutes(patientToHospTime);

                    try (Connection conn = Database.getConnection()) {
                        ambulanceDao.updateStatus(conn, ambId, AmbulanceStatus.EN_ROUTE_HOSPITAL, false, hosp.getLocation().getId());
                    } catch (SQLException ignored) {}

                    System.out.printf("[AUTO-SIM] Ambulance %s reached patient %s (%s). Now transporting to %s.%n",
                            ambId, req.getPatientName(), req.getEmergencyLocation().getName(), hosp.getName());
                }
            }
        }, SIM_LEG1_SECONDS, TimeUnit.SECONDS);

        // Stage 2: Arrival at destination hospital -> Updates position and ETA, but DO NOT automatically complete the trip!
        // The mission stays active until the operator manually clicks "Complete Trip & Admit" (/api/complete-trip).
        ScheduledFuture<?> leg2Future = scheduler.schedule(() -> {
            synchronized (dispatchLock) {
                if (amb.getAssignedRequest() == req) {
                    amb.setCurrentLocation(hosp.getLocation());
                    amb.setEtaMinutes(0.0);

                    try (Connection conn = Database.getConnection()) {
                        ambulanceDao.updateStatus(conn, ambId, AmbulanceStatus.EN_ROUTE_HOSPITAL, false, hosp.getLocation().getId());
                    } catch (SQLException ignored) {}

                    System.out.printf("[AUTO-SIM] Ambulance %s arrived at destination hospital %s with patient %s. Standing by for manual trip completion & patient admission.%n",
                            ambId, hosp.getName(), req.getPatientName());
                }
            }
        }, SIM_LEG1_SECONDS + SIM_LEG2_SECONDS, TimeUnit.SECONDS);

        futures.add(leg1Future);
        futures.add(leg2Future);
        activeSimulations.put(ambId, futures);
    }

    private void cancelSimulation(String ambulanceId) {
        List<ScheduledFuture<?>> existing = activeSimulations.remove(ambulanceId);
        if (existing != null) {
            for (ScheduledFuture<?> f : existing) {
                f.cancel(false);
            }
        }
    }

    /**
     * Cancels all actively running trip simulations (used during system reset).
     */
    public void cancelAllSimulations() {
        for (List<ScheduledFuture<?>> list : activeSimulations.values()) {
            for (ScheduledFuture<?> f : list) {
                f.cancel(false);
            }
        }
        activeSimulations.clear();
    }

    /**
     * Completes a trip, archives the mission, frees the vehicle, and updates the database.
     */
    public void completeTripInternal(Ambulance amb, EmergencyRequest req, Hospital hosp, double durationMins) {
        cancelSimulation(amb.getAmbulanceId());

        // In-memory update
        dispatchManager.completeTrip(amb);

        // Database updates
        String hospId = hosp != null ? hosp.getId() : "H1";
        String newLocId = amb.getCurrentLocation().getId();

        ambulanceDao.completeTrip(amb.getAmbulanceId(), newLocId);

        if (req != null) {
            emergencyDao.updateStatus(req.getRequestId(), "COMPLETED", amb.getAmbulanceId(), hospId);
            tripDao.insertTrip(req.getRequestId(), req.getPatientName(), amb.getAmbulanceId(), hospId, durationMins);
        } else {
            tripDao.insertTrip("REQ-LEGACY", "Direct Dispatch", amb.getAmbulanceId(), hospId, durationMins);
        }

        // Release hospital bed after patient handover so beds circulate dynamically
        if (hosp != null) {
            scheduler.schedule(() -> {
                synchronized (dispatchLock) {
                    hosp.releaseBed();
                    try (Connection conn = Database.getConnection()) {
                        hospitalDao.updateAvailableBeds(conn, hosp.getId(), hosp.getAvailableBeds());
                    } catch (SQLException ignored) {}
                    trigger();
                }
            }, 10, TimeUnit.SECONDS);
        }
    }

    /**
     * Manual trip completion called from HTTP handler /api/complete-trip.
     */
    public void completeTripManually(Ambulance amb) {
        EmergencyRequest req = amb.getAssignedRequest();
        Hospital hosp = amb.getAssignedHospital();
        completeTripInternal(amb, req, hosp, 15.0);
    }

    /**
     * Atomic database update for dispatch action.
     */
    private void persistDispatchTransaction(Ambulance amb, Hospital hosp, EmergencyRequest req) {
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                ambulanceDao.updateStatus(conn, amb.getAmbulanceId(), amb.getStatus(), false, req.getEmergencyLocation().getId());
                hospitalDao.updateAvailableBeds(conn, hosp.getId(), hosp.getAvailableBeds());
                emergencyDao.updateStatus(conn, req.getRequestId(), "DISPATCHED", amb.getAmbulanceId(), hosp.getId());
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                System.err.println("[AutoDispatcher] Transaction rolled back: " + ex.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("[AutoDispatcher] Database error: " + e.getMessage());
        }
    }

    // Getters and Control Methods
    public boolean isAutoDispatchEnabled() {
        return autoDispatchEnabled;
    }

    public void setAutoDispatchEnabled(boolean autoDispatchEnabled) {
        this.autoDispatchEnabled = autoDispatchEnabled;
        if (autoDispatchEnabled) {
            trigger();
        }
    }

    public boolean isPreemptionEnabled() {
        return preemptionEnabled;
    }

    public void setPreemptionEnabled(boolean preemptionEnabled) {
        this.preemptionEnabled = preemptionEnabled;
    }

    public int getActiveSimulationsCount() {
        return activeSimulations.size();
    }
}
