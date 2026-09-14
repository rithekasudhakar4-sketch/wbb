package algorithm;

import graph.RoadGraph;
import model.Location;
import model.Road;

import java.util.*;

public class RoutePlanner {

    private final RoadGraph graph;

    public RoutePlanner(RoadGraph graph) {
        this.graph = graph;
    }

    public List<Location> findShortestRoute(
            Location source,
            Location destination) {

        Map<Location, Double> distances = new HashMap<>();
        Map<Location, Location> previous = new HashMap<>();

        PriorityQueue<LocationDistance> priorityQueue =
                new PriorityQueue<>(
                        Comparator.comparingDouble(
                                LocationDistance::getDistance
                        )
                );

        for (Location location : graph.getLocations()) {
            distances.put(location, Double.MAX_VALUE);
        }

        distances.put(source, 0.0);

        priorityQueue.add(
                new LocationDistance(source, 0.0)
        );

        while (!priorityQueue.isEmpty()) {

            LocationDistance current =
                    priorityQueue.poll();

            Location currentLocation =
                    current.getLocation();

            double currentDistance =
                    current.getDistance();

            if (currentLocation.equals(destination)) {
                break;
            }

            if (currentDistance >
                    distances.get(currentLocation)) {
                continue;
            }

            for (Road road :
                    graph.getRoads(currentLocation)) {

                Location neighbor =
                        road.getTo();

                double newDistance =
                        currentDistance
                        + road.getTravelTime();

                if (newDistance <
                        distances.get(neighbor)) {

                    distances.put(
                            neighbor,
                            newDistance
                    );

                    previous.put(
                            neighbor,
                            currentLocation
                    );

                    priorityQueue.add(
                            new LocationDistance(
                                    neighbor,
                                    newDistance
                            )
                    );
                }
            }
        }

        return buildPath(
                previous,
                source,
                destination
        );
    }

    private List<Location> buildPath(
            Map<Location, Location> previous,
            Location source,
            Location destination) {

        List<Location> path =
                new ArrayList<>();

        Location current = destination;

        while (current != null) {

            path.add(current);

            if (current.equals(source)) {
                break;
            }

            current = previous.get(current);
        }

        if (!path.get(path.size() - 1).equals(source)) {
            return new ArrayList<>();
        }

        Collections.reverse(path);

        return path;
    }

    public double calculateRouteTime(
            List<Location> path) {

        double totalTime = 0;

        for (int i = 0; i < path.size() - 1; i++) {

            Location from = path.get(i);
            Location to = path.get(i + 1);

            for (Road road : graph.getRoads(from)) {

                if (road.getTo().equals(to)) {

                    totalTime += road.getTravelTime();
                    break;
                }
            }
        }

        return totalTime;
    }

    private static class LocationDistance {

        private final Location location;
        private final double distance;

        public LocationDistance(
                Location location,
                double distance) {

            this.location = location;
            this.distance = distance;
        }

        public Location getLocation() {
            return location;
        }

        public double getDistance() {
            return distance;
        }
    }
}