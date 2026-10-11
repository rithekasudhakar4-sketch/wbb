package model;

import java.util.Objects;

/**
 * Represents a geographical location / zone in the town of Nalam Nagar.
 */
public class Location {
    private final String id;
    private final String name;
    private final double latitude;
    private final double longitude;
    private final int x;
    private final int y;

    public Location(String id, String name, double latitude, double longitude) {
        this(id, name, latitude, longitude, defaultX(id), defaultY(id));
    }

    public Location(String id, String name, double latitude, double longitude, int x, int y) {
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.x = x;
        this.y = y;
    }

    /**
     * Default visual SVG map layout coordinates for Nalam Nagar zones (viewBox: 0 0 950 620).
     */
    public static int defaultX(String id) {
        if (id == null) return 400;
        switch (id) {
            case "L1": return 390; // Central Hospital Junction
            case "L2": return 580; // Market Circle
            case "L3": return 740; // Residential Colony A
            case "L4": return 200; // Industrial Area
            case "L5": return 750; // College Road
            case "L6": return 540; // Town Bus Terminal
            case "L7": return 330; // Greenfield Suburb
            case "L8": return 770; // Tech Park Zone
            case "L9": return 280; // Old City Bazaar
            case "L10": return 440; // Highway Bypass Junction
            case "L11": return 160; // Riverside Gardens
            case "L12": return 530; // North Gate Hub
            default: return 400;
        }
    }

    public static int defaultY(String id) {
        if (id == null) return 300;
        switch (id) {
            case "L1": return 290; // Central Hospital Junction
            case "L2": return 390; // Market Circle
            case "L3": return 270; // Residential Colony A
            case "L4": return 490; // Industrial Area
            case "L5": return 130; // College Road
            case "L6": return 190; // Town Bus Terminal
            case "L7": return 130; // Greenfield Suburb
            case "L8": return 500; // Tech Park Zone
            case "L9": return 380; // Old City Bazaar
            case "L10": return 530; // Highway Bypass Junction
            case "L11": return 230; // Riverside Gardens
            case "L12": return 70;  // North Gate Hub
            default: return 300;
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        return Objects.equals(id, location.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return name;
    }
}