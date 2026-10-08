package graph;

import model.Location;
import model.Road;

import java.util.*;

/**
 * Adjacency List Graph representing the road network of Nalam Nagar.
 * Supports positive travel time edge weights, live dynamic traffic delay updates,
 * and road closure management.
 */
public class RoadGraph {

    private final Map<Location, List<Road>> adjacencyList;
    private final Map<String, Location> locationById;
    private final Map<String, Location> locationByName;

    public RoadGraph() {
        this.adjacencyList = new HashMap<>();
        this.locationById = new HashMap<>();
        this.locationByName = new HashMap<>();
    }

    public void addLocation(Location location) {
        if (location == null) return;
        adjacencyList.putIfAbsent(location, new ArrayList<>());
        locationById.put(location.getId(), location);
        locationByName.put(location.getName().toLowerCase(), location);
    }

    public void addDirectedRoad(Location from, Location to, double distance, double travelTime) {
        addLocation(from);
        addLocation(to);
        Road road = new Road(from, to, distance, travelTime, false);
        adjacencyList.get(from).add(road);
    }

    public void addBidirectionalRoad(Location location1, Location location2, double distance, double travelTime) {
        addDirectedRoad(location1, location2, distance, travelTime);
        addDirectedRoad(location2, location1, distance, travelTime);
    }

    public List<Road> getRoads(Location location) {
        return adjacencyList.getOrDefault(location, Collections.emptyList());
    }

    public List<Road> getActiveRoads(Location location) {
        List<Road> active = new ArrayList<>();
        for (Road road : getRoads(location)) {
            if (!road.isClosed()) {
                active.add(road);
            }
        }
        return active;
    }

    public Set<Location> getLocations() {
        return Collections.unmodifiableSet(adjacencyList.keySet());
    }

    public Location getLocationById(String id) {
        return locationById.get(id);
    }

    public Location getLocationByName(String name) {
        if (name == null) return null;
        return locationByName.get(name.trim().toLowerCase());
    }

    public Road findRoad(Location from, Location to) {
        for (Road road : getRoads(from)) {
            if (road.getTo().equals(to)) {
                return road;
            }
        }
        return null;
    }

    public boolean setRoadClosed(Location loc1, Location loc2, boolean closed) {
        boolean updated = false;
        Road r1 = findRoad(loc1, loc2);
        if (r1 != null) {
            r1.setClosed(closed);
            updated = true;
        }
        Road r2 = findRoad(loc2, loc1);
        if (r2 != null) {
            r2.setClosed(closed);
            updated = true;
        }
        return updated;
    }

    public boolean updateTravelTime(Location loc1, Location loc2, double newTravelTime) {
        if (newTravelTime <= 0) return false;
        boolean updated = false;
        Road r1 = findRoad(loc1, loc2);
        if (r1 != null) {
            r1.setTravelTime(newTravelTime);
            updated = true;
        }
        Road r2 = findRoad(loc2, loc1);
        if (r2 != null) {
            r2.setTravelTime(newTravelTime);
            updated = true;
        }
        return updated;
    }

    public List<Road> getAllUniqueRoads() {
        List<Road> roads = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (List<Road> roadList : adjacencyList.values()) {
            for (Road r : roadList) {
                String key1 = r.getFrom().getId() + "-" + r.getTo().getId();
                String key2 = r.getTo().getId() + "-" + r.getFrom().getId();
                if (!seen.contains(key1) && !seen.contains(key2)) {
                    seen.add(key1);
                    roads.add(r);
                }
            }
        }
        return roads;
    }

    public void displayGraph() {
        System.out.println("\n===============================================================");
        System.out.println("                 NALAM NAGAR ROAD NETWORK MAP");
        System.out.println("===============================================================");
        for (Location location : adjacencyList.keySet()) {
            System.out.printf("%n[*] %s (%s):%n", location.getName(), location.getId());
            for (Road road : adjacencyList.get(location)) {
                String status = road.isClosed() ? " [CLOSED - NO PASSAGE]" : " [OPEN]";
                System.out.printf("    └──> %-28s | %4.1f km | %4.1f min%s%n",
                        road.getTo().getName(), road.getDistance(), road.getTravelTime(), status);
            }
        }
        System.out.println("===============================================================\n");
    }
}