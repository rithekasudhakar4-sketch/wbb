package model;

/**
 * Represents a directed road connection between two locations with distance, travel time, and closure status.
 */
public class Road {
    private final Location from;
    private final Location to;
    private final double distance;
    private double travelTime;
    private boolean closed;

    public Road(Location from, Location to, double distance, double travelTime) {
        this(from, to, distance, travelTime, false);
    }

    public Road(Location from, Location to, double distance, double travelTime, boolean closed) {
        this.from = from;
        this.to = to;
        this.distance = distance;
        this.travelTime = travelTime;
        this.closed = closed;
    }

    public Location getFrom() {
        return from;
    }

    public Location getTo() {
        return to;
    }

    public double getDistance() {
        return distance;
    }

    public double getTravelTime() {
        return travelTime;
    }

    public void setTravelTime(double travelTime) {
        this.travelTime = travelTime;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    @Override
    public String toString() {
        return from.getName() + " -> " + to.getName()
                + " | " + distance + " km | " + travelTime + " min"
                + (closed ? " [CLOSED]" : " [OPEN]");
    }
}