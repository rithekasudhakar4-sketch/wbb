package model;

public class Ambulance {
    private final String ambulanceId;
    private Location currentLocation;
    private Location targetLocation;
    private AmbulanceStatus status;

    public Ambulance(
            String ambulanceId,
            Location currentLocation,
            AmbulanceStatus status) {

        this.ambulanceId = ambulanceId;
        this.currentLocation = currentLocation;
        this.status = status;
        this.targetLocation = null;
    }

    public Ambulance(
            String ambulanceId,
            Location currentLocation,
            boolean available) {

        this(ambulanceId, currentLocation, available ? AmbulanceStatus.AVAILABLE : AmbulanceStatus.BUSY);
    }

    public String getAmbulanceId() {
        return ambulanceId;
    }

    public Location getCurrentLocation() {
        return currentLocation;
    }

    public Location getTargetLocation() {
        return targetLocation;
    }

    public AmbulanceStatus getStatus() {
        return status;
    }

    public boolean isAvailable() {
        return status == AmbulanceStatus.AVAILABLE;
    }

    public String getStatusString() {
        return status.name();
    }

    public void setCurrentLocation(Location currentLocation) {
        this.currentLocation = currentLocation;
    }

    public void setTargetLocation(Location targetLocation) {
        this.targetLocation = targetLocation;
    }

    public void setStatus(AmbulanceStatus status) {
        this.status = status;
    }

    public void setAvailable(boolean available) {
        this.status = available ? AmbulanceStatus.AVAILABLE : AmbulanceStatus.BUSY;
    }

    @Override
    public String toString() {
        return ambulanceId + " | Location: " + currentLocation.getName() + " | Status: " + status;
    }
}