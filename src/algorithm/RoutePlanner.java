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

    /**
     * Executes Dijkstra's algorithm while recording an audit trace of every step,
     * priority queue state, distance table transition, and edge relaxation.
     * Uses the EXACT same algorithmic logic as the production ambulance dispatcher.
     *
     * @param source Starting zone
     * @param destination Target zone
     * @return DijkstraTrace object containing step-by-step telemetry
     */
    public model.DijkstraTrace traceRoute(Location source, Location destination) {
        model.DijkstraTrace trace = new model.DijkstraTrace();

        if (source == null || destination == null) {
            trace.setReachable(false);
            trace.setStatusMessage("Invalid route request: Source or destination zone is missing.");
            return trace;
        }

        trace.setSourceId(source.getId());
        trace.setSourceName(source.getName());
        trace.setDestinationId(destination.getId());
        trace.setDestinationName(destination.getName());

        // Handle trivial case (source == destination)
        if (source.equals(destination)) {
            trace.setReachable(true);
            trace.setPath(Collections.singletonList(source));
            trace.setTotalTime(0.0);
            trace.setTotalDistance(0.0);
            trace.setStatusMessage("Source and destination are identical (" + source.getName() + "). Travel time is 0.0 min.");

            model.DijkstraTrace.Step step0 = new model.DijkstraTrace.Step(
                    1, source.getId(), source.getName(), 0.0,
                    "DESTINATION_REACHED",
                    "Source zone is the destination zone (" + source.getName() + "). Zero travel time required.",
                    6
            );
            step0.setVisitedNodes(Collections.singletonList(source.getId()));
            step0.setPriorityQueue(Collections.emptyList());
            Map<String, model.DijkstraTrace.TableEntry> dt = new LinkedHashMap<>();
            for (Location loc : graph.getLocations()) {
                double d = loc.equals(source) ? 0.0 : Double.MAX_VALUE;
                dt.put(loc.getId(), new model.DijkstraTrace.TableEntry(
                        loc.getId(), loc.getName(), d == Double.MAX_VALUE ? null : d,
                        loc.equals(source) ? source.getId() : null,
                        loc.equals(source) ? source.getName() : null,
                        loc.equals(source)
                ));
            }
            step0.setDistanceTable(dt);
            trace.getSteps().add(step0);
            return trace;
        }

        Map<Location, Double> minTime = new HashMap<>();
        Map<Location, Location> previous = new HashMap<>();
        Set<Location> visited = new LinkedHashSet<>();
        PriorityQueue<NodeTime> pq = new PriorityQueue<>(Comparator.comparingDouble(NodeTime::getTime));

        // Initialize distances
        for (Location loc : graph.getLocations()) {
            minTime.put(loc, Double.MAX_VALUE);
        }
        minTime.put(source, 0.0);
        pq.add(new NodeTime(source, 0.0));

        int stepCounter = 1;

        // Step 1: Initialization
        model.DijkstraTrace.Step initStep = new model.DijkstraTrace.Step(
                stepCounter++, source.getId(), source.getName(), 0.0,
                "INITIALIZATION",
                String.format(Locale.US, "Initialize Dijkstra: Set distance to source zone %s (%s) to 0.0 min, and all other %d zones to ∞ (infinity). Insert %s into the Priority Queue.",
                        source.getName(), source.getId(), graph.getLocations().size() - 1, source.getName()),
                3
        );
        initStep.setVisitedNodes(Collections.emptyList());
        initStep.setPriorityQueue(createPqSnapshot(pq));
        initStep.setDistanceTable(createDistanceTableSnapshot(minTime, previous, Collections.singleton(source.getId())));
        trace.getSteps().add(initStep);

        boolean destinationSettled = false;

        while (!pq.isEmpty()) {
            NodeTime current = pq.poll();
            Location u = current.getLocation();
            double timeU = current.getTime();

            // Stale entry check: if this node has already been settled via a shorter path
            if (visited.contains(u)) {
                model.DijkstraTrace.Step staleStep = new model.DijkstraTrace.Step(
                        stepCounter++, u.getId(), u.getName(), timeU,
                        "SKIP_STALE",
                        String.format(Locale.US, "Extracted %s (%.1f min) from Priority Queue, but this zone has already been settled earlier. Skip stale queue entry.",
                                u.getName(), timeU),
                        5
                );
                staleStep.setVisitedNodes(toNodeIdList(visited));
                staleStep.setPriorityQueue(createPqSnapshot(pq));
                staleStep.setDistanceTable(createDistanceTableSnapshot(minTime, previous, Collections.emptySet()));
                trace.getSteps().add(staleStep);
                continue;
            }

            visited.add(u);

            // Check if destination is reached and settled with optimal time
            if (u.equals(destination)) {
                destinationSettled = true;
                model.DijkstraTrace.Step destStep = new model.DijkstraTrace.Step(
                        stepCounter++, u.getId(), u.getName(), timeU,
                        "DESTINATION_REACHED",
                        String.format(Locale.US, "Target destination %s settled with optimal travel time of %.1f min! Dijkstra terminates as the guaranteed shortest path is confirmed.",
                                destination.getName(), timeU),
                        6
                );
                destStep.setVisitedNodes(toNodeIdList(visited));
                destStep.setPriorityQueue(createPqSnapshot(pq));
                destStep.setDistanceTable(createDistanceTableSnapshot(minTime, previous, Collections.singleton(u.getId())));
                trace.getSteps().add(destStep);
                break;
            }

            // Evaluate and relax all connecting roads from u
            List<model.DijkstraTrace.RelaxationCheck> relaxations = new ArrayList<>();
            Set<String> updatedNodeIds = new HashSet<>();

            List<Road> allRoadsFromU = graph.getRoads(u);
            StringBuilder expDetails = new StringBuilder();
            int improvedCount = 0;

            for (Road road : allRoadsFromU) {
                Location v = road.getTo();
                double edgeWeight = road.getTravelTime();
                double edgeDist = road.getDistance();
                Double oldDist = minTime.get(v);
                Double oldDistVal = (oldDist == null || oldDist == Double.MAX_VALUE) ? null : oldDist;

                if (road.isClosed()) {
                    relaxations.add(new model.DijkstraTrace.RelaxationCheck(
                            u.getId(), u.getName(), v.getId(), v.getName(),
                            edgeWeight, edgeDist, oldDistVal, null,
                            false, true, "Skipped: Road is CLOSED / BLOCKED by emergency barrier"
                    ));
                } else if (visited.contains(v)) {
                    relaxations.add(new model.DijkstraTrace.RelaxationCheck(
                            u.getId(), u.getName(), v.getId(), v.getName(),
                            edgeWeight, edgeDist, oldDistVal, null,
                            false, true, "Skipped: " + v.getName() + " is already settled / finalized"
                    ));
                } else {
                    double altTime = timeU + edgeWeight;
                    if (altTime < minTime.get(v)) {
                        minTime.put(v, altTime);
                        previous.put(v, u);
                        pq.add(new NodeTime(v, altTime));
                        updatedNodeIds.add(v.getId());
                        improvedCount++;

                        String oldStr = oldDistVal == null ? "∞" : String.format(Locale.US, "%.1f min", oldDistVal);
                        String reason = String.format(Locale.US, "Improved: %.1f + %.1f = %.1f min < %s. Distance updated!",
                                timeU, edgeWeight, altTime, oldStr);
                        relaxations.add(new model.DijkstraTrace.RelaxationCheck(
                                u.getId(), u.getName(), v.getId(), v.getName(),
                                edgeWeight, edgeDist, oldDistVal, altTime,
                                true, false, reason
                        ));

                        if (expDetails.length() > 0) expDetails.append("; ");
                        expDetails.append(String.format(Locale.US, "Updated %s to %.1f min (via %s)", v.getName(), altTime, u.getName()));
                    } else {
                        String reason = String.format(Locale.US, "No improvement: %.1f + %.1f = %.1f min >= %.1f min.",
                                timeU, edgeWeight, altTime, oldDist);
                        relaxations.add(new model.DijkstraTrace.RelaxationCheck(
                                u.getId(), u.getName(), v.getId(), v.getName(),
                                edgeWeight, edgeDist, oldDistVal, altTime,
                                false, false, reason
                        ));
                    }
                }
            }

            String stepExp;
            if (improvedCount > 0) {
                stepExp = String.format(Locale.US, "Extracted %s (%.1f min) with smallest distance from Priority Queue. Inspected %d connecting road(s): %s.",
                        u.getName(), timeU, allRoadsFromU.size(), expDetails.toString());
            } else {
                stepExp = String.format(Locale.US, "Extracted %s (%.1f min) with smallest distance from Priority Queue. Checked %d connecting road(s); no shorter routes discovered.",
                        u.getName(), timeU, allRoadsFromU.size());
            }

            model.DijkstraTrace.Step relaxStep = new model.DijkstraTrace.Step(
                    stepCounter++, u.getId(), u.getName(), timeU,
                    "RELAX_EDGES",
                    stepExp,
                    9
            );
            relaxStep.setVisitedNodes(toNodeIdList(visited));
            relaxStep.setPriorityQueue(createPqSnapshot(pq));
            relaxStep.setDistanceTable(createDistanceTableSnapshot(minTime, previous, updatedNodeIds));
            relaxStep.setRelaxations(relaxations);
            trace.getSteps().add(relaxStep);
        }

        // Reconstruct path
        if (destinationSettled || previous.containsKey(destination)) {
            List<Location> path = new ArrayList<>();
            Location curr = destination;
            while (curr != null) {
                path.add(curr);
                if (curr.equals(source)) break;
                curr = previous.get(curr);
            }

            if (!path.isEmpty() && path.get(path.size() - 1).equals(source)) {
                Collections.reverse(path);
                double totalTime = minTime.get(destination);
                double totalDistance = 0.0;

                for (int i = 0; i < path.size() - 1; i++) {
                    Road r = graph.findRoad(path.get(i), path.get(i + 1));
                    if (r != null) totalDistance += r.getDistance();
                }

                trace.setReachable(true);
                trace.setPath(path);
                trace.setTotalTime(totalTime);
                trace.setTotalDistance(totalDistance);
                trace.setStatusMessage(String.format(Locale.US, "Optimal route found: %s (Total: %.1f min / %.1f km)",
                        formatPathString(path), totalTime, totalDistance));

                // Calculate 2nd best route by temporarily closing each road in optimal path
                calculateSecondBestRoute(trace, source, destination, path, totalTime);
                return trace;
            }
        }

        trace.setReachable(false);
        trace.setStatusMessage(String.format(Locale.US, "Destination zone %s is UNREACHABLE from %s due to road closures or disconnected roads.",
                destination.getName(), source.getName()));
        return trace;
    }

    private void calculateSecondBestRoute(model.DijkstraTrace trace, Location source, Location destination,
                                          List<Location> optimalPath, double optimalTime) {
        if (optimalPath.size() < 2) return;

        double bestAltTime = Double.MAX_VALUE;
        List<Location> bestAltPath = null;

        for (int i = 0; i < optimalPath.size() - 1; i++) {
            Location u = optimalPath.get(i);
            Location v = optimalPath.get(i + 1);

            Road r1 = graph.findRoad(u, v);
            Road r2 = graph.findRoad(v, u);
            if (r1 == null) continue;

            boolean prevClosed1 = r1.isClosed();
            boolean prevClosed2 = r2 != null && r2.isClosed();

            // Temporarily close this segment
            r1.setClosed(true);
            if (r2 != null) r2.setClosed(true);

            RouteResult alt = planRoute(source, destination);

            // Restore road state
            r1.setClosed(prevClosed1);
            if (r2 != null) r2.setClosed(prevClosed2);

            if (alt.isReachable() && alt.getTotalTime() >= optimalTime) {
                // Ensure alternative path is not identical
                if (!pathsEqual(alt.getPath(), optimalPath)) {
                    if (alt.getTotalTime() < bestAltTime) {
                        bestAltTime = alt.getTotalTime();
                        bestAltPath = alt.getPath();
                    }
                }
            }
        }

        if (bestAltPath != null && bestAltTime < Double.MAX_VALUE) {
            trace.setSecondBestPath(bestAltPath);
            trace.setSecondBestTime(bestAltTime);
            trace.setTimeSaved(Math.max(0.0, bestAltTime - optimalTime));
        }
    }

    private boolean pathsEqual(List<Location> p1, List<Location> p2) {
        if (p1 == null || p2 == null || p1.size() != p2.size()) return false;
        for (int i = 0; i < p1.size(); i++) {
            if (!p1.get(i).equals(p2.get(i))) return false;
        }
        return true;
    }

    private String formatPathString(List<Location> path) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) sb.append(" -> ");
            sb.append(path.get(i).getName());
        }
        return sb.toString();
    }

    private List<String> toNodeIdList(Set<Location> visited) {
        List<String> list = new ArrayList<>();
        for (Location loc : visited) {
            list.add(loc.getId());
        }
        return list;
    }

    private List<model.DijkstraTrace.PqItem> createPqSnapshot(PriorityQueue<NodeTime> pq) {
        List<NodeTime> copy = new ArrayList<>(pq);
        copy.sort(Comparator.comparingDouble(NodeTime::getTime));
        List<model.DijkstraTrace.PqItem> list = new ArrayList<>();
        for (NodeTime nt : copy) {
            list.add(new model.DijkstraTrace.PqItem(nt.getLocation().getId(), nt.getLocation().getName(), nt.getTime()));
        }
        return list;
    }

    private Map<String, model.DijkstraTrace.TableEntry> createDistanceTableSnapshot(
            Map<Location, Double> minTime, Map<Location, Location> previous, Set<String> updatedNodeIds) {
        Map<String, model.DijkstraTrace.TableEntry> map = new LinkedHashMap<>();
        // Maintain consistent L1..L12 ordering
        List<Location> sortedLocs = new ArrayList<>(graph.getLocations());
        sortedLocs.sort(Comparator.comparing(Location::getId));

        for (Location loc : sortedLocs) {
            Double d = minTime.get(loc);
            Double distVal = (d == null || d == Double.MAX_VALUE) ? null : d;
            Location prev = previous.get(loc);
            String prevId = prev != null ? prev.getId() : null;
            String prevName = prev != null ? prev.getName() : null;
            boolean updated = updatedNodeIds.contains(loc.getId());

            map.put(loc.getId(), new model.DijkstraTrace.TableEntry(
                    loc.getId(), loc.getName(), distVal, prevId, prevName, updated
            ));
        }
        return map;
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