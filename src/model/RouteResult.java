package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Encapsulates the result of a Dijkstra route planning calculation.
 */
public class RouteResult {
    private final List<Location> path;
    private final double totalDistance;
    private final double totalTime;
    private final boolean reachable;

    public RouteResult(List<Location> path, double totalDistance, double totalTime, boolean reachable) {
        this.path = path != null ? path : new ArrayList<>();
        this.totalDistance = totalDistance;
        this.totalTime = totalTime;
        this.reachable = reachable;
    }

    public static RouteResult unreachable() {
        return new RouteResult(Collections.emptyList(), 0.0, Double.MAX_VALUE, false);
    }

    public List<Location> getPath() {
        return Collections.unmodifiableList(path);
    }

    public double getTotalDistance() {
        return totalDistance;
    }

    public double getTotalTime() {
        return totalTime;
    }

    public boolean isReachable() {
        return reachable;
    }

    public String getFormattedPath() {
        if (!reachable || path.isEmpty()) {
            return "No Route Available (Disconnected or Blocked Roads)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.size(); i++) {
            sb.append(path.get(i).getName());
            if (i < path.size() - 1) {
                sb.append(" -> ");
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("%s (%.1f km, %.1f min)", getFormattedPath(), totalDistance, totalTime);
    }
}
