package model;

public class Road {
    private final Location from;
    private final Location to;
    private final double distance;
    private final double travelTime;

    public Road(Location from, Location to, double distance, double travelTime) {
        this.from = from;
        this.to = to;
        this.distance = distance;
        this.travelTime = travelTime;
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

    @Override
    public String toString() {
        return from.getName() + " -> " + to.getName()
                + " | Distance: " + distance + " km"
                + " | Time: " + travelTime + " min";
    }
}