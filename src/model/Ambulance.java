package model;

public class Ambulance {
    private final String ambulanceId;
    private Location currentLocation;
    private boolean available;

    public Ambulance(
            String ambulanceId,
            Location currentLocation,
            boolean available) {

        this.ambulanceId = ambulanceId;
        this.currentLocation = currentLocation;
        this.available = available;
    }

    public String getAmbulanceId() {
        return ambulanceId;
    }

    public Location getCurrentLocation() {
        return currentLocation;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setCurrentLocation(Location currentLocation) {
        this.currentLocation = currentLocation;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public String toString() {
        return "Ambulance{" +
                "id='" + ambulanceId + '\'' +
                ", location=" + currentLocation.getName() +
                ", available=" + available +
                '}';
    }
}