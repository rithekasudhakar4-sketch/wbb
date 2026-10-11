package model;

import java.util.*;

/**
 * Data structures representing an audit trace of Dijkstra's Shortest Path Algorithm.
 * Captures full state progression (priority queue, distance table, edge relaxations,
 * visited list, and educational explanation) at each algorithmic iteration.
 */
public class DijkstraTrace {

    private String sourceId;
    private String sourceName;
    private String destinationId;
    private String destinationName;
    private boolean reachable;
    private String statusMessage;
    private List<Location> path = new ArrayList<>();
    private double totalTime;
    private double totalDistance;

    // Optional comparison with 2nd best route
    private List<Location> secondBestPath = new ArrayList<>();
    private double secondBestTime;
    private double timeSaved;

    // Ordered sequence of execution steps
    private List<Step> steps = new ArrayList<>();

    public DijkstraTrace() {}

    // --------------------------------------------------------------------------
    // Inner Data Classes
    // --------------------------------------------------------------------------

    public static class Step {
        private int stepNumber;
        private String currentNodeId;
        private String currentNodeName;
        private double currentDistance;
        private String action; // INITIALIZATION, EXTRACT_MIN, DESTINATION_REACHED, RELAX_EDGES, SKIP_STALE
        private String explanation;
        private int pseudocodeLine;
        private List<String> visitedNodes = new ArrayList<>();
        private List<PqItem> priorityQueue = new ArrayList<>();
        private Map<String, TableEntry> distanceTable = new LinkedHashMap<>();
        private List<RelaxationCheck> relaxations = new ArrayList<>();

        public Step(int stepNumber, String currentNodeId, String currentNodeName, double currentDistance,
                    String action, String explanation, int pseudocodeLine) {
            this.stepNumber = stepNumber;
            this.currentNodeId = currentNodeId;
            this.currentNodeName = currentNodeName;
            this.currentDistance = currentDistance;
            this.action = action;
            this.explanation = explanation;
            this.pseudocodeLine = pseudocodeLine;
        }

        public int getStepNumber() { return stepNumber; }
        public String getCurrentNodeId() { return currentNodeId; }
        public String getCurrentNodeName() { return currentNodeName; }
        public double getCurrentDistance() { return currentDistance; }
        public String getAction() { return action; }
        public String getExplanation() { return explanation; }
        public int getPseudocodeLine() { return pseudocodeLine; }
        public List<String> getVisitedNodes() { return visitedNodes; }
        public List<PqItem> getPriorityQueue() { return priorityQueue; }
        public Map<String, TableEntry> getDistanceTable() { return distanceTable; }
        public List<RelaxationCheck> getRelaxations() { return relaxations; }

        public void setVisitedNodes(List<String> visitedNodes) { this.visitedNodes = visitedNodes; }
        public void setPriorityQueue(List<PqItem> priorityQueue) { this.priorityQueue = priorityQueue; }
        public void setDistanceTable(Map<String, TableEntry> distanceTable) { this.distanceTable = distanceTable; }
        public void setRelaxations(List<RelaxationCheck> relaxations) { this.relaxations = relaxations; }
    }

    public static class PqItem {
        private final String nodeId;
        private final String nodeName;
        private final double distance;

        public PqItem(String nodeId, String nodeName, double distance) {
            this.nodeId = nodeId;
            this.nodeName = nodeName;
            this.distance = distance;
        }

        public String getNodeId() { return nodeId; }
        public String getNodeName() { return nodeName; }
        public double getDistance() { return distance; }
    }

    public static class TableEntry {
        private final String nodeId;
        private final String nodeName;
        private final Double distance; // null if infinity
        private final String previousId;
        private final String previousName;
        private final boolean updatedInThisStep;

        public TableEntry(String nodeId, String nodeName, Double distance,
                          String previousId, String previousName, boolean updatedInThisStep) {
            this.nodeId = nodeId;
            this.nodeName = nodeName;
            this.distance = distance;
            this.previousId = previousId;
            this.previousName = previousName;
            this.updatedInThisStep = updatedInThisStep;
        }

        public String getNodeId() { return nodeId; }
        public String getNodeName() { return nodeName; }
        public Double getDistance() { return distance; }
        public String getPreviousId() { return previousId; }
        public String getPreviousName() { return previousName; }
        public boolean isUpdatedInThisStep() { return updatedInThisStep; }
    }

    public static class RelaxationCheck {
        private final String fromId;
        private final String fromName;
        private final String toId;
        private final String toName;
        private final double edgeWeight; // minutes
        private final double edgeDistanceKm;
        private final Double oldDistance;
        private final Double newDistance;
        private final boolean improved;
        private final boolean skipped;
        private final String reason;

        public RelaxationCheck(String fromId, String fromName, String toId, String toName,
                               double edgeWeight, double edgeDistanceKm, Double oldDistance,
                               Double newDistance, boolean improved, boolean skipped, String reason) {
            this.fromId = fromId;
            this.fromName = fromName;
            this.toId = toId;
            this.toName = toName;
            this.edgeWeight = edgeWeight;
            this.edgeDistanceKm = edgeDistanceKm;
            this.oldDistance = oldDistance;
            this.newDistance = newDistance;
            this.improved = improved;
            this.skipped = skipped;
            this.reason = reason;
        }

        public String getFromId() { return fromId; }
        public String getFromName() { return fromName; }
        public String getToId() { return toId; }
        public String getToName() { return toName; }
        public double getEdgeWeight() { return edgeWeight; }
        public double getEdgeDistanceKm() { return edgeDistanceKm; }
        public Double getOldDistance() { return oldDistance; }
        public Double getNewDistance() { return newDistance; }
        public boolean isImproved() { return improved; }
        public boolean isSkipped() { return skipped; }
        public String getReason() { return reason; }
    }

    // --------------------------------------------------------------------------
    // Getters & Setters
    // --------------------------------------------------------------------------

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }

    public String getDestinationId() { return destinationId; }
    public void setDestinationId(String destinationId) { this.destinationId = destinationId; }

    public String getDestinationName() { return destinationName; }
    public void setDestinationName(String destinationName) { this.destinationName = destinationName; }

    public boolean isReachable() { return reachable; }
    public void setReachable(boolean reachable) { this.reachable = reachable; }

    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }

    public List<Location> getPath() { return path; }
    public void setPath(List<Location> path) { this.path = path; }

    public double getTotalTime() { return totalTime; }
    public void setTotalTime(double totalTime) { this.totalTime = totalTime; }

    public double getTotalDistance() { return totalDistance; }
    public void setTotalDistance(double totalDistance) { this.totalDistance = totalDistance; }

    public List<Location> getSecondBestPath() { return secondBestPath; }
    public void setSecondBestPath(List<Location> secondBestPath) { this.secondBestPath = secondBestPath; }

    public double getSecondBestTime() { return secondBestTime; }
    public void setSecondBestTime(double secondBestTime) { this.secondBestTime = secondBestTime; }

    public double getTimeSaved() { return timeSaved; }
    public void setTimeSaved(double timeSaved) { this.timeSaved = timeSaved; }

    public List<Step> getSteps() { return steps; }
    public void setSteps(List<Step> steps) { this.steps = steps; }

    // --------------------------------------------------------------------------
    // Clean JSON Serialization (Zero External Libraries)
    // --------------------------------------------------------------------------

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"sourceId\":").append(quote(sourceId)).append(",");
        sb.append("\"sourceName\":").append(quote(sourceName)).append(",");
        sb.append("\"destinationId\":").append(quote(destinationId)).append(",");
        sb.append("\"destinationName\":").append(quote(destinationName)).append(",");
        sb.append("\"reachable\":").append(reachable).append(",");
        sb.append("\"statusMessage\":").append(quote(statusMessage)).append(",");
        sb.append(String.format(Locale.US, "\"totalTime\":%.2f,", totalTime));
        sb.append(String.format(Locale.US, "\"totalDistance\":%.2f,", totalDistance));

        // Path
        sb.append("\"path\":[");
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) sb.append(",");
            Location loc = path.get(i);
            sb.append(String.format(Locale.US, "{\"id\":%s,\"name\":%s,\"x\":%d,\"y\":%d}",
                    quote(loc.getId()), quote(loc.getName()), loc.getX(), loc.getY()));
        }
        sb.append("],");

        // Second best path
        sb.append("\"secondBestPath\":[");
        for (int i = 0; i < secondBestPath.size(); i++) {
            if (i > 0) sb.append(",");
            Location loc = secondBestPath.get(i);
            sb.append(String.format(Locale.US, "{\"id\":%s,\"name\":%s}", quote(loc.getId()), quote(loc.getName())));
        }
        sb.append("],");
        sb.append(String.format(Locale.US, "\"secondBestTime\":%.2f,", secondBestTime));
        sb.append(String.format(Locale.US, "\"timeSaved\":%.2f,", timeSaved));

        // Steps
        sb.append("\"steps\":[");
        for (int i = 0; i < steps.size(); i++) {
            if (i > 0) sb.append(",");
            Step s = steps.get(i);
            sb.append("{");
            sb.append("\"stepNumber\":").append(s.getStepNumber()).append(",");
            sb.append("\"currentNodeId\":").append(quote(s.getCurrentNodeId())).append(",");
            sb.append("\"currentNodeName\":").append(quote(s.getCurrentNodeName())).append(",");
            sb.append(String.format(Locale.US, "\"currentDistance\":%.2f,", s.getCurrentDistance()));
            sb.append("\"action\":").append(quote(s.getAction())).append(",");
            sb.append("\"explanation\":").append(quote(s.getExplanation())).append(",");
            sb.append("\"pseudocodeLine\":").append(s.getPseudocodeLine()).append(",");

            // Visited nodes
            sb.append("\"visitedNodes\":[");
            for (int v = 0; v < s.getVisitedNodes().size(); v++) {
                if (v > 0) sb.append(",");
                sb.append(quote(s.getVisitedNodes().get(v)));
            }
            sb.append("],");

            // Priority Queue
            sb.append("\"priorityQueue\":[");
            for (int q = 0; q < s.getPriorityQueue().size(); q++) {
                if (q > 0) sb.append(",");
                PqItem item = s.getPriorityQueue().get(q);
                sb.append(String.format(Locale.US, "{\"nodeId\":%s,\"nodeName\":%s,\"distance\":%.2f}",
                        quote(item.getNodeId()), quote(item.getNodeName()), item.getDistance()));
            }
            sb.append("],");

            // Distance Table
            sb.append("\"distanceTable\":{");
            int dtCount = 0;
            for (Map.Entry<String, TableEntry> entry : s.getDistanceTable().entrySet()) {
                if (dtCount > 0) sb.append(",");
                TableEntry te = entry.getValue();
                String distVal = te.getDistance() == null ? "null" : String.format(Locale.US, "%.2f", te.getDistance());
                sb.append(quote(entry.getKey())).append(":{");
                sb.append("\"nodeId\":").append(quote(te.getNodeId())).append(",");
                sb.append("\"nodeName\":").append(quote(te.getNodeName())).append(",");
                sb.append("\"distance\":").append(distVal).append(",");
                sb.append("\"previousId\":").append(te.getPreviousId() == null ? "null" : quote(te.getPreviousId())).append(",");
                sb.append("\"previousName\":").append(te.getPreviousName() == null ? "null" : quote(te.getPreviousName())).append(",");
                sb.append("\"updatedInThisStep\":").append(te.isUpdatedInThisStep());
                sb.append("}");
                dtCount++;
            }
            sb.append("},");

            // Relaxations
            sb.append("\"relaxations\":[");
            for (int r = 0; r < s.getRelaxations().size(); r++) {
                if (r > 0) sb.append(",");
                RelaxationCheck rc = s.getRelaxations().get(r);
                String oldD = rc.getOldDistance() == null ? "null" : String.format(Locale.US, "%.2f", rc.getOldDistance());
                String newD = rc.getNewDistance() == null ? "null" : String.format(Locale.US, "%.2f", rc.getNewDistance());
                sb.append("{");
                sb.append("\"fromId\":").append(quote(rc.getFromId())).append(",");
                sb.append("\"fromName\":").append(quote(rc.getFromName())).append(",");
                sb.append("\"toId\":").append(quote(rc.getToId())).append(",");
                sb.append("\"toName\":").append(quote(rc.getToName())).append(",");
                sb.append(String.format(Locale.US, "\"edgeWeight\":%.2f,", rc.getEdgeWeight()));
                sb.append(String.format(Locale.US, "\"edgeDistanceKm\":%.2f,", rc.getEdgeDistanceKm()));
                sb.append("\"oldDistance\":").append(oldD).append(",");
                sb.append("\"newDistance\":").append(newD).append(",");
                sb.append("\"improved\":").append(rc.isImproved()).append(",");
                sb.append("\"skipped\":").append(rc.isSkipped()).append(",");
                sb.append("\"reason\":").append(quote(rc.getReason()));
                sb.append("}");
            }
            sb.append("]");

            sb.append("}");
        }
        sb.append("]");

        sb.append("}");
        return sb.toString();
    }

    private static String quote(String val) {
        if (val == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : val.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
