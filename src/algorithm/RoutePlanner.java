package algorithm;

import graph.RoadGraph;
import model.Location;
import model.Road;
import model.RouteResult;

import java.util.*;

/**
 * Implements Dijkstra's Shortest Path Algorithm on the RoadGraph.
 * Optimizes strictly by travel time in minutes while skipping closed roads.
 * Time Complexity: O((V + E) log V) using a binary heap (PriorityQueue).
 */
public class RoutePlanner {

    private final RoadGraph graph;

    public RoutePlanner(RoadGraph graph) {
        this.graph = graph;
    }

    /**
     * Executes Dijkstra's algorithm to find the optimal path from source to destination.
     *
     * @param source Starting location
     * @param destination Target location
     * @return RouteResult containing path, total distance, travel time, and reachability flag
     */
    public RouteResult planRoute(Location source, Location destination) {
        if (source == null || destination == null) {
            return RouteResult.unreachable();
        }

        if (source.equals(destination)) {
            return new RouteResult(Collections.singletonList(source), 0.0, 0.0, true);
        }

        Map<Location, Double> minTime = new HashMap<>();
        Map<Location, Location> previous = new HashMap<>();
        Map<Location, Double> edgeDistances = new HashMap<>();

        PriorityQueue<NodeTime> pq = new PriorityQueue<>(Comparator.comparingDouble(NodeTime::getTime));

        for (Location loc : graph.getLocations()) {
            minTime.put(loc, Double.MAX_VALUE);
        }

        minTime.put(source, 0.0);
        pq.add(new NodeTime(source, 0.0));

        while (!pq.isEmpty()) {
            NodeTime current = pq.poll();
            Location u = current.getLocation();
            double timeU = current.getTime();

            if (u.equals(destination)) {
                break; // Target settled with optimal time
            }

            if (timeU > minTime.get(u)) {
                continue; // Stale queue entry
            }

            for (Road road : graph.getActiveRoads(u)) {
                Location v = road.getTo();
                double altTime = timeU + road.getTravelTime();

                if (altTime < minTime.get(v)) {
                    minTime.put(v, altTime);
                    previous.put(v, u);
                    edgeDistances.put(v, road.getDistance());
                    pq.add(new NodeTime(v, altTime));
                }
            }
        }

        if (!previous.containsKey(destination) && !source.equals(destination)) {
            return RouteResult.unreachable();
        }

        List<Location> path = new ArrayList<>();
        Location curr = destination;
        while (curr != null) {
            path.add(curr);
            if (curr.equals(source)) break;
            curr = previous.get(curr);
        }

        if (path.isEmpty() || !path.get(path.size() - 1).equals(source)) {
            return RouteResult.unreachable();
        }

        Collections.reverse(path);

        // Calculate accurate total distance & time along reconstructed path
        double totalTime = minTime.get(destination);
        double totalDistance = 0.0;

        for (int i = 0; i < path.size() - 1; i++) {
            Location from = path.get(i);
            Location to = path.get(i + 1);
            Road r = graph.findRoad(from, to);
            if (r != null) {
                totalDistance += r.getDistance();
            }
        }

        return new RouteResult(path, totalDistance, totalTime, true);
    }

    /**
     * Backward-compatible helper returning list of path locations.
     */
    public List<Location> findShortestRoute(Location source, Location destination) {
        RouteResult result = planRoute(source, destination);
        return result.isReachable() ? result.getPath() : Collections.emptyList();
    }

    /**
     * Calculates travel time across a sequence of locations.
     */
    public double calculateRouteTime(List<Location> path) {
        if (path == null || path.size() < 2) return 0.0;
        double totalTime = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            Location from = path.get(i);
            Location to = path.get(i + 1);
            Road road = graph.findRoad(from, to);
            if (road != null && !road.isClosed()) {
                totalTime += road.getTravelTime();
            } else {
                return Double.MAX_VALUE; // Infeasible path due to closure/disconnect
            }
        }
        return totalTime;
    }

    private static class NodeTime {
        private final Location location;
        private final double time;

        public NodeTime(Location location, double time) {
            this.location = location;
            this.time = time;
        }

        public Location getLocation() {
            return location;
        }

        public double getTime() {
            return time;
        }
    }
}