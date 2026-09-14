package service;

import algorithm.RoutePlanner;
import model.Ambulance;
import model.AmbulanceStatus;
import model.Location;

import java.util.ArrayList;
import java.util.List;

public class AmbulanceManager {
    private final List<Ambulance> ambulances;

    public AmbulanceManager() {
        this.ambulances = new ArrayList<>();
    }

    public void addAmbulance(Ambulance ambulance) {
        ambulances.add(ambulance);
    }

    public List<Ambulance> getAllAmbulances() {
        return ambulances;
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

    public void completeTrip(Ambulance ambulance) {
        if (ambulance.getTargetLocation() != null) {
            ambulance.setCurrentLocation(ambulance.getTargetLocation());
            ambulance.setTargetLocation(null);
        }
        ambulance.setStatus(AmbulanceStatus.AVAILABLE);
    }

    public Ambulance findFastestAmbulance(Location destination, RoutePlanner routePlanner) {
        Ambulance bestAmbulance = null;
        double minTime = Double.MAX_VALUE;

        for (Ambulance amb : ambulances) {
            if (amb.isAvailable()) {
                List<Location> route = routePlanner.findShortestRoute(amb.getCurrentLocation(), destination);
                if (!route.isEmpty()) {
                    double travelTime = routePlanner.calculateRouteTime(route);
                    if (travelTime < minTime) {
                        minTime = travelTime;
                        bestAmbulance = amb;
                    }
                }
            }
        }

        return bestAmbulance;
    }
}

