package model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a medical facility/hospital in Nalam Nagar with specialized capabilities and bed capacity.
 */
public class Hospital {
    private final String id;
    private final String name;
    private final Location location;
    private final Set<Capability> capabilities;
    private final int totalBeds;
    private int availableBeds;

    public Hospital(String id, String name, Location location, Set<Capability> capabilities, int totalBeds, int availableBeds) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.capabilities = new HashSet<>(capabilities);
        this.totalBeds = totalBeds;
        this.availableBeds = availableBeds;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Location getLocation() {
        return location;
    }

    public Set<Capability> getCapabilities() {
        return Collections.unmodifiableSet(capabilities);
    }

    public boolean hasCapability(Capability capability) {
        return capabilities.contains(capability);
    }

    public int getTotalBeds() {
        return totalBeds;
    }

    public int getAvailableBeds() {
        return availableBeds;
    }

    public boolean hasAvailableBed() {
        return availableBeds > 0;
    }

    public synchronized boolean admitPatient() {
        if (availableBeds > 0) {
            availableBeds--;
            return true;
        }
        return false;
    }

    public synchronized void releaseBed() {
        if (availableBeds < totalBeds) {
            availableBeds++;
        }
    }

    public void setAvailableBeds(int beds) {
        this.availableBeds = Math.max(0, Math.min(beds, totalBeds));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Hospital hospital = (Hospital) o;
        return Objects.equals(id, hospital.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return name + " [" + location.getName() + "] | Beds: " + availableBeds + "/" + totalBeds;
    }
}
