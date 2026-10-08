package model;

import java.util.Objects;

/**
 * Represents an emergency response vehicle in the town's fleet.
 */
public class Ambulance {
    private final String ambulanceId;
    private Location currentLocation;
    private Location targetLocation;
    private AmbulanceStatus status;
    private Hospital assignedHospital;
    private EmergencyRequest assignedRequest;
    private int totalTripsCompleted;

    public Ambulance(String ambulanceId, Location currentLocation, AmbulanceStatus status) {
        this.ambulanceId = ambulanceId;
        this.currentLocation = currentLocation;
        this.status = status;
        this.targetLocation = null;
        this.assignedHospital = null;
        this.assignedRequest = null;
        this.totalTripsCompleted = 0;
    }

    public Ambulance(String ambulanceId, Location currentLocation, boolean available) {
        this(ambulanceId, currentLocation, available ? AmbulanceStatus.AVAILABLE : AmbulanceStatus.BUSY);
    }

    public String getAmbulanceId() {
        return ambulanceId;
    }

    public Location getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(Location currentLocation) {
        this.currentLocation = currentLocation;
    }

    public Location getTargetLocation() {
        return targetLocation;
    }

    public void setTargetLocation(Location targetLocation) {
        this.targetLocation = targetLocation;
    }

    public AmbulanceStatus getStatus() {
        return status;
    }

    public void setStatus(AmbulanceStatus status) {
        this.status = status;
    }

    public boolean isAvailable() {
        return status == AmbulanceStatus.AVAILABLE;
    }

    public void setAvailable(boolean available) {
        this.status = available ? AmbulanceStatus.AVAILABLE : AmbulanceStatus.BUSY;
    }

    public String getStatusString() {
        return status.getDescription();
    }

    public Hospital getAssignedHospital() {
        return assignedHospital;
    }

    public void setAssignedHospital(Hospital assignedHospital) {
        this.assignedHospital = assignedHospital;
    }

    public EmergencyRequest getAssignedRequest() {
        return assignedRequest;
    }

    public void setAssignedRequest(EmergencyRequest assignedRequest) {
        this.assignedRequest = assignedRequest;
    }

    public int getTotalTripsCompleted() {
        return totalTripsCompleted;
    }

    public void incrementTrips() {
        this.totalTripsCompleted++;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Ambulance ambulance = (Ambulance) o;
        return Objects.equals(ambulanceId, ambulance.ambulanceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ambulanceId);
    }

    @Override
    public String toString() {
        return String.format("%-6s | Station: %-25s | Status: %-18s | Trips: %d",
                ambulanceId, currentLocation.getName(), status.getDescription(), totalTripsCompleted);
    }
}