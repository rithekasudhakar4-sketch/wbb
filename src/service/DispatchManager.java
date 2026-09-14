package service;

import algorithm.RoutePlanner;
import model.Ambulance;
import model.AmbulanceStatus;
import model.EmergencyRequest;
import model.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

public class DispatchManager {
    private final PriorityQueue<EmergencyRequest> requestQueue;
    private final AmbulanceManager ambulanceManager;
    private final RoutePlanner routePlanner;

    public DispatchManager(AmbulanceManager ambulanceManager, RoutePlanner routePlanner) {
        this.requestQueue = new PriorityQueue<>();
        this.ambulanceManager = ambulanceManager;
        this.routePlanner = routePlanner;
    }

    public void addEmergencyRequest(EmergencyRequest request) {
        requestQueue.add(request);
        System.out.println("----------------------------------------");
        System.out.println("Emergency Request Created");
        System.out.println("----------------------------------------");
        System.out.println("Name     : " + request.getPatientName());
        System.out.println("Location : " + request.getEmergencyLocation().getName());
        System.out.println("Severity : " + request.getSeverity());
    }

    public List<EmergencyRequest> getPendingRequests() {
        // Return sorted list representation of priority queue without mutating it
        PriorityQueue<EmergencyRequest> copy = new PriorityQueue<>(requestQueue);
        List<EmergencyRequest> pendingList = new ArrayList<>();
        while (!copy.isEmpty()) {
            pendingList.add(copy.poll());
        }
        return pendingList;
    }

    public boolean hasPendingRequests() {
        return !requestQueue.isEmpty();
    }

    public void dispatchNextEmergency() {
        if (requestQueue.isEmpty()) {
            System.out.println("\n[!] No pending emergency requests to dispatch.");
            return;
        }

        List<Ambulance> availableAmbulances = ambulanceManager.getAvailableAmbulances();
        if (availableAmbulances.isEmpty()) {
            System.out.println("\n========================================");
            System.out.println("       DISPATCH UNAVAILABLE");
            System.out.println("========================================");
            System.out.println("\nNo ambulances are currently available.");
            System.out.println("\nEmergency remains in the pending queue.");
            return;
        }

        EmergencyRequest request = requestQueue.peek(); // Inspect highest priority request
        Location emergencyLoc = request.getEmergencyLocation();

        System.out.println("\nSearching for available ambulances...\n");

        Ambulance selectedAmbulance = null;
        List<Location> bestRoute = null;
        double minTime = Double.MAX_VALUE;

        for (Ambulance amb : availableAmbulances) {
            Location ambLoc = amb.getCurrentLocation();
            List<Location> route = routePlanner.findShortestRoute(ambLoc, emergencyLoc);
            if (route.isEmpty()) {
                System.out.println("No route available from " + amb.getAmbulanceId() + " to " + emergencyLoc.getName() + ".");
            } else {
                double travelTime = routePlanner.calculateRouteTime(route);
                System.out.printf("%s: %s -> %s = %.1f minutes%n",
                        amb.getAmbulanceId(), ambLoc.getName(), emergencyLoc.getName(), travelTime);

                if (travelTime < minTime) {
                    minTime = travelTime;
                    selectedAmbulance = amb;
                    bestRoute = route;
                }
            }
        }

        if (selectedAmbulance == null || bestRoute == null) {
            System.out.println("\nNo available ambulance can currently reach this emergency.");
            System.out.println("Emergency remains in the pending queue.");
            return;
        }

        // Dequeue request now that ambulance is assigned
        requestQueue.poll();

        System.out.println("\nSelected Ambulance : " + selectedAmbulance.getAmbulanceId());
        System.out.printf("Estimated Time     : %.1f minutes%n", minTime);

        System.out.println("\nRoute:");
        StringBuilder routeStr = new StringBuilder();
        for (int i = 0; i < bestRoute.size(); i++) {
            routeStr.append(bestRoute.get(i).getName());
            if (i < bestRoute.size() - 1) {
                routeStr.append(" -> ");
            }
        }
        System.out.println(routeStr.toString());

        // Update ambulance status to BUSY and set target destination without moving current location yet
        selectedAmbulance.setStatus(AmbulanceStatus.BUSY);
        selectedAmbulance.setTargetLocation(emergencyLoc);

        System.out.println("\nAmbulance " + selectedAmbulance.getAmbulanceId() + " dispatched successfully.");
        System.out.println("Status: BUSY");
    }
}

