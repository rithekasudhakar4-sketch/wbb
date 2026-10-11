package service;

import algorithm.RoutePlanner;
import graph.RoadGraph;
import model.Ambulance;
import model.AmbulanceStatus;
import model.Location;
import model.RouteResult;

import java.util.*;

/**
 * Manages the town's fleet of emergency ambulances, tracks availability,
 * and finds the fastest responding ambulance using Dijkstra's shortest path.
 */
public class AmbulanceManager {
    private final List<Ambulance> ambulances;
    private final Map<String, Ambulance> ambulanceById;

    public AmbulanceManager() {
        this.ambulances = new ArrayList<>();
        this.ambulanceById = new HashMap<>();
    }

    public void addAmbulance(Ambulance ambulance) {
        if (ambulance == null) return;
        ambulances.add(ambulance);
        ambulanceById.put(ambulance.getAmbulanceId(), ambulance);
    }

    public List<Ambulance> getAllAmbulances() {
        return Collections.unmodifiableList(ambulances);
    }

    public Ambulance getAmbulanceById(String id) {
        return ambulanceById.get(id);
    }

    public List<Ambulance> getAvailableAmbulances() {
        List<Ambulance> availableList = new ArrayList<>();
        for (Ambulance amb : ambulances) {
            if (amb.isAvailable()) {
                availableList.add(amb);
            }
        }
        return availableList;
    }

    public List<Ambulance> getBusyAmbulances() {
        List<Ambulance> busyList = new ArrayList<>();
        for (Ambulance amb : ambulances) {
            if (!amb.isAvailable()) {
                busyList.add(amb);
            }
        }
        return busyList;
    }

    /**
     * Evaluates all available ambulances and chooses the one with the lowest travel time
     * to the patient's location.
     */
    public AmbulanceSelectionResult findFastestAmbulance(Location patientLocation, RoutePlanner routePlanner) {
        List<Ambulance> available = getAvailableAmbulances();
        if (available.isEmpty()) {
            return new AmbulanceSelectionResult(null, RouteResult.unreachable());
        }

        System.out.println("---------------------------------------------------------------");
        System.out.printf("Evaluating %d available ambulance(s) for response time...%n", available.size());
        System.out.println("---------------------------------------------------------------");

        Ambulance bestAmbulance = null;
        RouteResult bestRoute = null;
        double minTime = Double.MAX_VALUE;

        for (Ambulance amb : available) {
            Location ambLoc = amb.getCurrentLocation();
            RouteResult route = routePlanner.planRoute(ambLoc, patientLocation);

            if (!route.isReachable()) {
                System.out.printf("  [-] %-6s (Station: %-22s) -- UNREACHABLE due to road closures [BLOCKED]%n",
                        amb.getAmbulanceId(), ambLoc.getName());
                continue;
            }

            System.out.printf("  [+] %-6s (Station: %-22s) — ETA: %4.1f min (%4.1f km) | Route: %s%n",
                    amb.getAmbulanceId(), ambLoc.getName(), route.getTotalTime(), route.getTotalDistance(),
                    route.getFormattedPath());

            if (route.getTotalTime() < minTime) {
                minTime = route.getTotalTime();
                bestAmbulance = amb;
                bestRoute = route;
            }
        }

        if (bestAmbulance != null) {
            System.out.println("---------------------------------------------------------------");
            System.out.printf(">> Optimal Ambulance Selected: %s from %s (ETA: %.1f min)%n",
                    bestAmbulance.getAmbulanceId(), bestAmbulance.getCurrentLocation().getName(), minTime);
            System.out.println("---------------------------------------------------------------");
        }

        return new AmbulanceSelectionResult(bestAmbulance, bestRoute);
    }

    /**
     * Completes an ambulance trip, moves it to its final destination (e.g. hospital),
     * and resets its status to AVAILABLE.
     */
    public void completeTrip(Ambulance ambulance) {
        if (ambulance == null) return;
        if (ambulance.getAssignedHospital() != null) {
            ambulance.setCurrentLocation(ambulance.getAssignedHospital().getLocation());
        } else if (ambulance.getTargetLocation() != null) {
            ambulance.setCurrentLocation(ambulance.getTargetLocation());
        }
        ambulance.setTargetLocation(null);
        ambulance.setStatus(AmbulanceStatus.AVAILABLE);
        ambulance.setAssignedRequest(null);
        ambulance.setAssignedHospital(null);
        ambulance.setEtaMinutes(0.0);
        ambulance.incrementTrips();
    }

    /**
     * Resets all ambulances to initial station locations, zero trips, and available status.
     */
    public void resetFleet(RoadGraph graph) {
        for (Ambulance amb : ambulances) {
            amb.setStatus(AmbulanceStatus.AVAILABLE);
            amb.setTargetLocation(null);
            amb.setAssignedHospital(null);
            amb.setAssignedRequest(null);
            amb.setEtaMinutes(0.0);
            amb.resetTrips();

            Location baseLoc = switch (amb.getAmbulanceId()) {
                case "AMB-1" -> graph.getLocationById("L1");
                case "AMB-2" -> graph.getLocationById("L6");
                case "AMB-3" -> graph.getLocationById("L4");
                case "AMB-4" -> graph.getLocationById("L12");
                case "AMB-5" -> graph.getLocationById("L2");
                default -> amb.getCurrentLocation();
            };
            if (baseLoc != null) {
                amb.setCurrentLocation(baseLoc);
            }
        }
    }

    public void displayFleet() {
        System.out.println("\n=========================================================================================");
        System.out.println("                             AMBULANCE FLEET STATUS");
        System.out.println("=========================================================================================");
        System.out.printf("%-6s | %-28s | %-20s | %-18s | %s%n",
                "ID", "Current Station", "Status", "Destination/Hospital", "Trips Done");
        System.out.println("-----------------------------------------------------------------------------------------");
        for (Ambulance amb : ambulances) {
            String target = amb.getTargetLocation() != null ? amb.getTargetLocation().getName() : "-";
            System.out.printf("%-6s | %-28s | %-20s | %-18s | %d%n",
                    amb.getAmbulanceId(),
                    amb.getCurrentLocation().getName(),
                    amb.getStatus().getDescription(),
                    target,
                    amb.getTotalTripsCompleted());
        }
        System.out.println("=========================================================================================\n");
    }

    public static class AmbulanceSelectionResult {
        private final Ambulance ambulance;
        private final RouteResult route;

        public AmbulanceSelectionResult(Ambulance ambulance, RouteResult route) {
            this.ambulance = ambulance;
            this.route = route;
        }

        public Ambulance getAmbulance() {
            return ambulance;
        }

        public RouteResult getRoute() {
            return route;
        }

        public boolean isSuccess() {
            return ambulance != null && route != null && route.isReachable();
        }
    }
}
