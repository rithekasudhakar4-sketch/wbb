package service;

import algorithm.RoutePlanner;
import graph.RoadGraph;
import model.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Central orchestrator that coordinates emergency intake, PriorityQueue triage,
 * hospital capability matching, nearest-by-time ambulance allocation, and end-to-end trip lifecycle.
 */
public class DispatchManager {

    private final PriorityQueue<EmergencyRequest> requestQueue;
    private final AmbulanceManager ambulanceManager;
    private final HospitalManager hospitalManager;
    private final RoutePlanner routePlanner;
    private final RoadGraph roadGraph;
    private final List<EmergencyRequest> resolvedRequests;
    private int totalRequestsProcessed;

    public DispatchManager(AmbulanceManager ambulanceManager, HospitalManager hospitalManager,
                           RoutePlanner routePlanner, RoadGraph roadGraph) {
        this.requestQueue = new PriorityQueue<>();
        this.ambulanceManager = ambulanceManager;
        this.hospitalManager = hospitalManager;
        this.routePlanner = routePlanner;
        this.roadGraph = roadGraph;
        this.resolvedRequests = new ArrayList<>();
        this.totalRequestsProcessed = 0;
    }

    public void addEmergencyRequest(EmergencyRequest request) {
        if (request == null) return;
        requestQueue.add(request);
        totalRequestsProcessed++;

        System.out.println("\n===============================================================");
        System.out.println("            EMERGENCY CALL RECORDED & TRIAGED");
        System.out.println("===============================================================");
        System.out.printf("Request ID         : %s%n", request.getRequestId());
        System.out.printf("Patient Name       : %s%n", request.getPatientName());
        System.out.printf("Emergency Zone     : %s%n", request.getEmergencyLocation().getName());
        System.out.printf("Medical Condition  : %s%n", request.getCondition() != null ? request.getCondition().getDisplayName() : "N/A");
        System.out.printf("Triage Severity    : %s (Priority Rank: %d)%n", request.getSeverity(), request.getSeverity().ordinal() + 1);
        System.out.printf("Required Facility  : %s%n", request.getRequiredCapability() != null ? request.getRequiredCapability().getDisplayName() : "N/A");
        System.out.printf("Timestamp          : %s%n", request.getFormattedTime());
        System.out.printf("Queue Position     : %d in pending triage queue%n", requestQueue.size());
        System.out.println("===============================================================\n");
    }

    public List<EmergencyRequest> getPendingRequests() {
        PriorityQueue<EmergencyRequest> copy = new PriorityQueue<>(requestQueue);
        List<EmergencyRequest> pending = new ArrayList<>();
        while (!copy.isEmpty()) {
            pending.add(copy.poll());
        }
        return pending;
    }

    public boolean hasPendingRequests() {
        return !requestQueue.isEmpty();
    }

    public int getPendingQueueSize() {
        return requestQueue.size();
    }

    /**
     * Executes end-to-end dispatch:
     * 1. Dequeues highest severity request (Critical > Serious > Minor)
     * 2. Evaluates hospitals for capability & available beds -> Selects fastest reachable hospital
     * 3. Evaluates ambulance fleet -> Selects fastest reachable available ambulance
     * 4. Calculates Look-ahead Total ETA (Ambulance->Patient + Patient->Hospital)
     * 5. Reserves bed, updates ambulance to BUSY, displays full reasoning
     */
    public boolean dispatchNextEmergency() {
        if (requestQueue.isEmpty()) {
            System.out.println("\n[!] No pending emergency requests in queue.");
            return false;
        }

        EmergencyRequest request = requestQueue.peek(); // Inspect highest priority request
        Location patientLoc = request.getEmergencyLocation();
        Capability requiredCap = request.getRequiredCapability() != null ?
                request.getRequiredCapability() : Capability.GENERAL_WARD;

        System.out.println("\n===============================================================");
        System.out.printf("PROCESSING HIGHEST PRIORITY DISPATCH: [%s] %s%n",
                request.getSeverity(), request.getPatientName());
        System.out.println("===============================================================");
        System.out.printf("Patient: %s | Condition: %s | Zone: %s | Severity: %s%n",
                request.getPatientName(),
                request.getCondition() != null ? request.getCondition().name() : "GENERAL",
                patientLoc.getName(),
                request.getSeverity());

        // Step 1: Hospital Capability & Bed Capacity Matching
        HospitalManager.HospitalSelectionResult hospitalResult =
                hospitalManager.evaluateAndSelectHospital(patientLoc, requiredCap, routePlanner);

        if (!hospitalResult.isSuccess()) {
            System.out.println("\n[!] DISPATCH HALTED: No hospital currently available with required facility and beds.");
            System.out.println("Request remains safely prioritized at the top of the queue.");
            return false;
        }

        // Step 2: Fastest Available Ambulance Selection
        AmbulanceManager.AmbulanceSelectionResult ambulanceResult =
                ambulanceManager.findFastestAmbulance(patientLoc, routePlanner);

        if (!ambulanceResult.isSuccess()) {
            System.out.println("\n===============================================================");
            System.out.println("                 DISPATCH UNAVAILABLE (FLEET BUSY)");
            System.out.println("===============================================================");
            System.out.println("All ambulances are currently busy or blocked by road closures.");
            System.out.println("Emergency request remains safely in the PriorityQueue for immediate next dispatch.");
            System.out.println("===============================================================\n");
            return false;
        }

        // Both Hospital and Ambulance successfully identified!
        requestQueue.poll(); // Dequeue from priority queue

        Hospital chosenHospital = hospitalResult.getHospital();
        RouteResult patientToHospitalRoute = hospitalResult.getRoute();

        Ambulance chosenAmbulance = ambulanceResult.getAmbulance();
        RouteResult ambulanceToPatientRoute = ambulanceResult.getRoute();

        // Calculate Look-ahead Total ETA
        double ambToPatientTime = ambulanceToPatientRoute.getTotalTime();
        double patientToHospTime = patientToHospitalRoute.getTotalTime();
        double totalLookaheadETA = ambToPatientTime + patientToHospTime;

        // Reserve Bed and Update Vehicle State
        chosenHospital.admitPatient();
        chosenAmbulance.setStatus(AmbulanceStatus.BUSY);
        chosenAmbulance.setTargetLocation(chosenHospital.getLocation());
        chosenAmbulance.setAssignedHospital(chosenHospital);
        chosenAmbulance.setAssignedRequest(request);

        request.setAssignedAmbulance(chosenAmbulance);
        request.setAssignedHospital(chosenHospital);
        request.setStatus("DISPATCHED");

        // Display Detailed Decision Explanation
        System.out.println("\n===============================================================");
        System.out.println("              DISPATCH DECISION & MISSION DISCLOSURE");
        System.out.println("===============================================================");
        System.out.printf(">> Assigned Ambulance     : %s (Origin: %s)%n",
                chosenAmbulance.getAmbulanceId(), chosenAmbulance.getCurrentLocation().getName());
        System.out.printf(">> Leg 1 Route (Amb->Pt)  : %s%n", ambulanceToPatientRoute.getFormattedPath());
        System.out.printf(">> Leg 1 ETA (Response)   : %.1f minutes (%.1f km)%n",
                ambToPatientTime, ambulanceToPatientRoute.getTotalDistance());
        System.out.println("---------------------------------------------------------------");
        System.out.printf(">> Chosen Hospital        : %s (%s)%n",
                chosenHospital.getName(), chosenHospital.getLocation().getName());
        System.out.printf(">> Leg 2 Route (Pt->Hosp) : %s%n", patientToHospitalRoute.getFormattedPath());
        System.out.printf(">> Leg 2 ETA (Transport)  : %.1f minutes (%.1f km)%n",
                patientToHospTime, patientToHospitalRoute.getTotalDistance());
        System.out.println("---------------------------------------------------------------");
        System.out.printf(">> TOTAL LOOK-AHEAD ETA   : %.1f MINUTES%n", totalLookaheadETA);
        System.out.printf(">> Hospital Bed Reserved  : Available Beds remaining: %d/%d%n",
                chosenHospital.getAvailableBeds(), chosenHospital.getTotalBeds());
        System.out.printf(">> Ambulance Status       : %s (Target Destination: %s)%n",
                chosenAmbulance.getStatus().getDescription(), chosenHospital.getLocation().getName());
        System.out.println("===============================================================\n");

        return true;
    }

    /**
     * Completes an in-progress mission, marks ambulance available at the hospital,
     * and archives the completed request.
     */
    public void completeTrip(Ambulance ambulance) {
        if (ambulance == null) return;
        EmergencyRequest req = ambulance.getAssignedRequest();
        Hospital hosp = ambulance.getAssignedHospital();

        if (req != null) {
            req.setStatus("RESOLVED");
            resolvedRequests.add(req);
        }

        ambulanceManager.completeTrip(ambulance);

        System.out.println("\n===============================================================");
        System.out.println("                MISSION COMPLETED SUCCESSFULLY");
        System.out.println("===============================================================");
        System.out.printf("Ambulance ID        : %s%n", ambulance.getAmbulanceId());
        System.out.printf("New Station Location: %s%n", ambulance.getCurrentLocation().getName());
        System.out.printf("Status              : %s%n", ambulance.getStatus().getDescription());
        if (hosp != null) {
            System.out.printf("Patient Admitted At : %s (Current Available Beds: %d/%d)%n",
                    hosp.getName(), hosp.getAvailableBeds(), hosp.getTotalBeds());
        }
        System.out.printf("Total Vehicle Trips : %d%n", ambulance.getTotalTripsCompleted());
        System.out.println("===============================================================\n");
    }

    public void displaySystemStatistics() {
        System.out.println("\n===============================================================");
        System.out.println("           NALAM NAGAR DISPATCH SYSTEM PERFORMANCE METRICS");
        System.out.println("===============================================================");
        System.out.printf("Total Requests Logged    : %d%n", totalRequestsProcessed);
        System.out.printf("Currently Pending In PQ  : %d%n", requestQueue.size());
        System.out.printf("Successfully Resolved    : %d%n", resolvedRequests.size());
        int availableAmbs = ambulanceManager.getAvailableAmbulances().size();
        int totalAmbs = ambulanceManager.getAllAmbulances().size();
        System.out.printf("Ambulance Fleet Status   : %d / %d Available (%.1f%% Busy)%n",
                availableAmbs, totalAmbs,
                totalAmbs > 0 ? ((double)(totalAmbs - availableAmbs) / totalAmbs) * 100.0 : 0.0);

        System.out.println("---------------------------------------------------------------");
        System.out.println("Hospital Bed Occupancy Summary:");
        for (Hospital h : hospitalManager.getAllHospitals()) {
            int occupied = h.getTotalBeds() - h.getAvailableBeds();
            double occRate = ((double) occupied / h.getTotalBeds()) * 100.0;
            System.out.printf("  * %-30s : %2d/%2d Beds Occupied (%5.1f%% Capacity)%n",
                    h.getName(), occupied, h.getTotalBeds(), occRate);
        }
        System.out.println("===============================================================\n");
    }

    public List<EmergencyRequest> getResolvedRequests() {
        return Collections.unmodifiableList(resolvedRequests);
    }
}
