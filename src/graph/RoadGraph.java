package graph;

import model.Location;
import model.Road;

import java.util.*;

public class RoadGraph {

    private final Map<Location, List<Road>> adjacencyList;

    public RoadGraph() {
        adjacencyList = new HashMap<>();
    }

    public void addLocation(Location location) {
        adjacencyList.putIfAbsent(location, new ArrayList<>());
    }

    public void addRoad(
            Location from,
            Location to,
            double distance,
            double travelTime) {

        addLocation(from);
        addLocation(to);

        Road road = new Road(
                from,
                to,
                distance,
                travelTime
        );

        adjacencyList.get(from).add(road);
    }

    public void addBidirectionalRoad(
            Location location1,
            Location location2,
            double distance,
            double travelTime) {

        addRoad(
                location1,
                location2,
                distance,
                travelTime
        );

        addRoad(
                location2,
                location1,
                distance,
                travelTime
        );
    }

    public List<Road> getRoads(Location location) {

        return adjacencyList.getOrDefault(
                location,
                new ArrayList<>()
        );
    }

    public Set<Location> getLocations() {
        return adjacencyList.keySet();
    }

    public void displayGraph() {

        System.out.println("\n========== ROAD NETWORK ==========");

        for (Location location : adjacencyList.keySet()) {

            System.out.println("\n" + location.getName() + ":");

            for (Road road : adjacencyList.get(location)) {

                System.out.println(
                        "  -> " + road.getTo().getName()
                        + " | "
                        + road.getDistance() + " km"
                        + " | "
                        + road.getTravelTime() + " min"
                );
            }
        }
    }
}