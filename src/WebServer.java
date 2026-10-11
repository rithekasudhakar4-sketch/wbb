import algorithm.RoutePlanner;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dao.*;
import db.Database;
import graph.RoadGraph;
import model.*;
import service.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

/**
 * Lightweight Embedded HTTP Web Server for Nalam Nagar Emergency Dispatch.
 * Connects the HTML5/CSS3 frontend to the Java backend algorithms, database, and services.
 */
public class WebServer {

    private static final int PORT = 8080;
    private static final String FRONTEND_DIR = "frontend";

    private final RoadGraph roadGraph;
    private final RoutePlanner routePlanner;
    private final HospitalManager hospitalManager;
    private final AmbulanceManager ambulanceManager;
    private final DispatchManager dispatchManager;

    // Database Access Objects (DAOs)
    private final RoadDao roadDao = new RoadDao();
    private final HospitalDao hospitalDao = new HospitalDao();
    private final AmbulanceDao ambulanceDao = new AmbulanceDao();
    private final EmergencyDao emergencyDao = new EmergencyDao();
    private final TripDao tripDao = new TripDao();

    // Thread-safety lock for coordinating HTTP requests and AutoDispatcher background thread
    private final Object dispatchLock = new Object();
    private final AutoDispatcher autoDispatcher;

    private int requestCounter = 1;

    // Optional Demo Mode Simulation (Generates randomized calls on a timer for demonstrations)
    private final ScheduledExecutorService demoExecutor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> demoFuture = null;
    private boolean demoModeEnabled = false;

    public WebServer() {
        // 0. Ensure SQL Database and Tables Exist
        Database.initDatabase();

        // 1. Initialize Network Graph & Locations from Database
        this.roadGraph = new RoadGraph();
        roadDao.loadNetworkIntoGraph(roadGraph);
        if (roadGraph.getLocations().isEmpty()) {
            System.out.println("[WebServer] DB roads empty, loading default network...");
            DataInitializer.initializeNetwork(roadGraph);
        }

        // 2. Initialize Route Planner
        this.routePlanner = new RoutePlanner(roadGraph);

        // 3. Initialize Hospital Manager from Database
        this.hospitalManager = new HospitalManager();
        List<Hospital> dbHospitals = hospitalDao.getAll(roadGraph);
        if (!dbHospitals.isEmpty()) {
            for (Hospital h : dbHospitals) {
                hospitalManager.addHospital(h);
            }
        } else {
            System.out.println("[WebServer] DB hospitals empty, loading from file/default...");
            hospitalManager.loadHospitalsFromFileOrDefault("hospitals.txt", roadGraph);
        }

        // 4. Initialize Ambulance Fleet from Database
        this.ambulanceManager = new AmbulanceManager();
        List<Ambulance> dbAmbulances = ambulanceDao.getAll(roadGraph);
        if (!dbAmbulances.isEmpty()) {
            for (Ambulance a : dbAmbulances) {
                ambulanceManager.addAmbulance(a);
            }
        } else {
            System.out.println("[WebServer] DB fleet empty, loading default ambulances...");
            DataInitializer.initializeAmbulances(ambulanceManager, roadGraph);
        }

        // 5. Initialize Dispatch Coordinator
        this.dispatchManager = new DispatchManager(ambulanceManager, hospitalManager, routePlanner, roadGraph);

        // 6. Restore Pending Emergencies from DB if existing (Runtime queue starts EMPTY by default)
        List<EmergencyRequest> pending = emergencyDao.getPendingRequests(roadGraph);
        if (!pending.isEmpty()) {
            for (EmergencyRequest req : pending) {
                dispatchManager.restoreEmergencyRequest(req);
            }
            System.out.printf("[WebServer] Restored %d pending emergency requests from database into PriorityQueue.%n", pending.size());
        } else {
            System.out.println("[WebServer] System runtime state initialized clean (0 pending emergencies, 0 active trips).");
        }

        // 7. Initialize request ID counter from DB records
        this.requestCounter = emergencyDao.getNextRequestIdNumber();

        // 8. Start Background Autonomous Dispatch Engine
        this.autoDispatcher = new AutoDispatcher(
            this.dispatchManager,
            this.ambulanceManager,
            this.hospitalManager,
            this.routePlanner,
            this.ambulanceDao,
            this.hospitalDao,
            this.emergencyDao,
            this.tripDao,
            this.dispatchLock
        );
        this.autoDispatcher.start();
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // API Contexts
        server.createContext("/api/status", new StatusHandler());
        server.createContext("/api/queue", new QueueHandler());
        server.createContext("/api/ambulances", new AmbulanceHandler());
        server.createContext("/api/hospitals", new HospitalHandler());
        server.createContext("/api/roads", new RoadHandler());
        server.createContext("/api/roads/update", new RoadUpdateHandler());
        server.createContext("/api/intake", new IntakeHandler());
        server.createContext("/api/dispatch", new DispatchHandler());
        server.createContext("/api/complete-trip", new CompleteTripHandler());
        server.createContext("/api/history", new HistoryHandler());
        server.createContext("/api/seed-samples", new SeedSamplesHandler());
        server.createContext("/api/reset", new ResetHandler());
        server.createContext("/api/demo/status", new DemoStatusHandler());
        server.createContext("/api/demo/toggle", new DemoToggleHandler());
        server.createContext("/api/auto-dispatch/status", new AutoDispatchStatusHandler());
        server.createContext("/api/auto-dispatch/toggle", new AutoDispatchToggleHandler());
        server.createContext("/api/dijkstra/trace", new DijkstraTraceHandler());
        server.createContext("/api/dijkstra/network", new DijkstraNetworkHandler());

        // Static File Context
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(null); // default executor
        server.start();

        System.out.println("==================================================================");
        System.out.println("   NALAM NAGAR EMERGENCY OPERATIONS - HTTP WEB SERVER STARTED");
        System.out.println("==================================================================");
        System.out.printf("  Local Web Portal : http://localhost:%d/%n", PORT);
        System.out.printf("  API Endpoint Base: http://localhost:%d/api/%n", PORT);
        System.out.println("  Connected directly to Java RoutePlanner, PriorityQueue & Graph.");
        System.out.println("==================================================================");
    }

    public static void main(String[] args) {
        try {
            WebServer webServer = new WebServer();
            webServer.start();
        } catch (Exception e) {
            System.err.println("Failed to start web server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================================================================
    // HTTP Handlers
    // =========================================================================

    /**
     * Serves static files from the `frontend/` directory.
     */
    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String pathStr = exchange.getRequestURI().getPath();
            if (pathStr.equals("/") || pathStr.isEmpty()) {
                pathStr = "/index.html";
            }

            // Remove leading slash
            if (pathStr.startsWith("/")) {
                pathStr = pathStr.substring(1);
            }

            Path filePath = Paths.get(FRONTEND_DIR, pathStr);

            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                String response = "404 Not Found: " + pathStr;
                sendResponse(exchange, 404, "text/plain", response.getBytes(StandardCharsets.UTF_8));
                return;
            }

            String contentType = getContentType(filePath.toString());
            byte[] fileBytes = Files.readAllBytes(filePath);
            sendResponse(exchange, 200, contentType, fileBytes);
        }
    }

    /**
     * Returns overall system status and KPI metrics.
     */
    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method Not Allowed\"}".getBytes());
                return;
            }

            int availableAmbs = ambulanceManager.getAvailableAmbulances().size();
            int totalAmbs = ambulanceManager.getAllAmbulances().size();
            int pendingCases = dispatchManager.getPendingQueueSize();
            int resolvedCases = dispatchManager.getResolvedRequests().size();

            int totalBeds = 0;
            int availableBeds = 0;
            for (Hospital h : hospitalManager.getAllHospitals()) {
                totalBeds += h.getTotalBeds();
                availableBeds += h.getAvailableBeds();
            }

            // Real calculations directly from live state and database
            double avgResponseTime = tripDao.getAverageResponseTime();
            double onTimeRate = tripDao.getOnTimeRate();

            String json = String.format(Locale.US,
                "{" +
                "\"availableAmbulances\":%d," +
                "\"totalAmbulances\":%d," +
                "\"activeTrips\":%d," +
                "\"pendingRequests\":%d," +
                "\"resolvedCases\":%d," +
                "\"availableBeds\":%d," +
                "\"totalBeds\":%d," +
                "\"avgResponseTime\":%.1f," +
                "\"onTimeRate\":%.1f" +
                "}",
                availableAmbs, totalAmbs, (totalAmbs - availableAmbs), pendingCases, resolvedCases,
                availableBeds, totalBeds, avgResponseTime, onTimeRate
            );

            sendResponse(exchange, 200, "application/json", json.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Returns pending emergency requests in PriorityQueue order.
     */
    private class QueueHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method Not Allowed\"}".getBytes());
                return;
            }

            List<EmergencyRequest> list = dispatchManager.getPendingRequests();
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                EmergencyRequest r = list.get(i);
                if (i > 0) sb.append(",");
                sb.append(String.format(Locale.US,
                    "{" +
                    "\"rank\":%d," +
                    "\"requestId\":\"%s\"," +
                    "\"patientName\":\"%s\"," +
                    "\"locationId\":\"%s\"," +
                    "\"locationName\":\"%s\"," +
                    "\"severity\":\"%s\"," +
                    "\"condition\":\"%s\"," +
                    "\"conditionDisplay\":\"%s\"," +
                    "\"requiredCapability\":\"%s\"," +
                    "\"formattedTime\":\"%s\"," +
                    "\"timestamp\":%d," +
                    "\"status\":\"%s\"," +
                    "\"assignedAmbulance\":\"%s\"," +
                    "\"assignedHospital\":\"%s\"" +
                    "}",
                    (i + 1),
                    escapeJson(r.getRequestId()),
                    escapeJson(r.getPatientName()),
                    escapeJson(r.getEmergencyLocation().getId()),
                    escapeJson(r.getEmergencyLocation().getName()),
                    escapeJson(r.getSeverity().name()),
                    escapeJson(r.getCondition() != null ? r.getCondition().name() : "GENERAL"),
                    escapeJson(r.getCondition() != null ? r.getCondition().getDisplayName() : "General Illness"),
                    escapeJson(r.getRequiredCapability() != null ? r.getRequiredCapability().name() : "GENERAL_WARD"),
                    escapeJson(r.getFormattedTime()),
                    r.getTimestamp(),
                    escapeJson(r.getStatus()),
                    escapeJson(r.getAssignedAmbulance() != null ? r.getAssignedAmbulance().getAmbulanceId() : "AUTO"),
                    escapeJson(r.getAssignedHospital() != null ? r.getAssignedHospital().getName() : "AUTO")
                ));
            }
            sb.append("]");

            sendResponse(exchange, 200, "application/json", sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Returns ambulance fleet readiness and location.
     */
    private class AmbulanceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method Not Allowed\"}".getBytes());
                return;
            }

            List<Ambulance> list = ambulanceManager.getAllAmbulances();
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                Ambulance a = list.get(i);
                if (i > 0) sb.append(",");

                String assignedEmergencyId = (a.getAssignedRequest() != null)
                        ? "\"" + escapeJson(a.getAssignedRequest().getRequestId()) + "\""
                        : "null";
                String patientName = (a.getAssignedRequest() != null)
                        ? "\"" + escapeJson(a.getAssignedRequest().getPatientName()) + "\""
                        : "null";
                String severity = (a.getAssignedRequest() != null && a.getAssignedRequest().getSeverity() != null)
                        ? "\"" + escapeJson(a.getAssignedRequest().getSeverity().name()) + "\""
                        : "null";
                String hospitalName = (a.getAssignedHospital() != null)
                        ? "\"" + escapeJson(a.getAssignedHospital().getName()) + "\""
                        : "null";
                String targetLoc = (a.getTargetLocation() != null)
                        ? "\"" + escapeJson(a.getTargetLocation().getName()) + "\""
                        : "null";
                String assignedReqIdStr = (a.getAssignedRequest() != null)
                        ? escapeJson(a.getAssignedRequest().getRequestId())
                        : "";

                sb.append(String.format(Locale.US,
                    "{" +
                    "\"id\":\"%s\"," +
                    "\"station\":\"%s\"," +
                    "\"currentZone\":\"%s\"," +
                    "\"isAvailable\":%b," +
                    "\"status\":\"%s\"," +
                    "\"assignedEmergencyId\":%s," +
                    "\"assignedRequestId\":\"%s\"," +
                    "\"patientName\":%s," +
                    "\"emergencySeverity\":%s," +
                    "\"hospitalName\":%s," +
                    "\"targetLocation\":%s," +
                    "\"etaMinutes\":%.1f," +
                    "\"totalTripsCompleted\":%d" +
                    "}",
                    escapeJson(a.getAmbulanceId()),
                    escapeJson(a.getCurrentLocation().getName()),
                    escapeJson(a.getCurrentLocation().getName()),
                    a.isAvailable(),
                    escapeJson(a.getStatus().name()),
                    assignedEmergencyId,
                    assignedReqIdStr,
                    patientName,
                    severity,
                    hospitalName,
                    targetLoc,
                    a.getEtaMinutes(),
                    a.getTotalTripsCompleted()
                ));
            }
            sb.append("]");

            sendResponse(exchange, 200, "application/json", sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Returns all hospitals, their capacity and medical capabilities.
     */
    private class HospitalHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method Not Allowed\"}".getBytes());
                return;
            }

            List<Hospital> list = hospitalManager.getAllHospitals();
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                Hospital h = list.get(i);
                if (i > 0) sb.append(",");

                StringBuilder caps = new StringBuilder("[");
                int capIdx = 0;
                for (Capability c : h.getCapabilities()) {
                    if (capIdx > 0) caps.append(",");
                    caps.append("\"").append(c.name()).append("\"");
                    capIdx++;
                }
                caps.append("]");

                sb.append(String.format(Locale.US,
                    "{" +
                    "\"id\":\"%s\"," +
                    "\"name\":\"%s\"," +
                    "\"locationId\":\"%s\"," +
                    "\"locationName\":\"%s\"," +
                    "\"availableBeds\":%d," +
                    "\"totalBeds\":%d," +
                    "\"capabilities\":%s" +
                    "}",
                    escapeJson(h.getId()),
                    escapeJson(h.getName()),
                    escapeJson(h.getLocation().getId()),
                    escapeJson(h.getLocation().getName()),
                    h.getAvailableBeds(),
                    h.getTotalBeds(),
                    caps.toString()
                ));
            }
            sb.append("]");

            sendResponse(exchange, 200, "application/json", sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Returns road connections, traffic conditions, and closures.
     */
    private class RoadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method Not Allowed\"}".getBytes());
                return;
            }

            Set<String> seenEdges = new HashSet<>();
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;

            for (Location loc : roadGraph.getLocations()) {
                for (Road r : roadGraph.getRoads(loc)) {
                    String key = loc.getId().compareTo(r.getTo().getId()) < 0 ?
                            loc.getId() + "-" + r.getTo().getId() :
                            r.getTo().getId() + "-" + loc.getId();

                    if (seenEdges.contains(key)) continue;
                    seenEdges.add(key);

                    if (!first) sb.append(",");
                    first = false;

                    sb.append(String.format(Locale.US,
                        "{" +
                        "\"fromId\":\"%s\"," +
                        "\"fromName\":\"%s\"," +
                        "\"fromX\":%d," +
                        "\"fromY\":%d," +
                        "\"toId\":\"%s\"," +
                        "\"toName\":\"%s\"," +
                        "\"toX\":%d," +
                        "\"toY\":%d," +
                        "\"distance\":%.1f," +
                        "\"travelTime\":%.1f," +
                        "\"isClosed\":%b" +
                        "}",
                        escapeJson(loc.getId()),
                        escapeJson(loc.getName()),
                        loc.getX(),
                        loc.getY(),
                        escapeJson(r.getTo().getId()),
                        escapeJson(r.getTo().getName()),
                        r.getTo().getX(),
                        r.getTo().getY(),
                        r.getDistance(),
                        r.getTravelTime(),
                        r.isClosed()
                    ));
                }
            }
            sb.append("]");

            sendResponse(exchange, 200, "application/json", sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * GET /api/dijkstra/network: Returns full graph network (all 12 zones with coordinates,
     * hospital and ambulance associations, plus all 16 unique road edges).
     */
    private class DijkstraNetworkHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method Not Allowed\"}".getBytes());
                return;
            }

            // Map hospitals to zones
            Map<String, Hospital> hospByLoc = new HashMap<>();
            for (Hospital h : hospitalManager.getAllHospitals()) {
                if (h.getLocation() != null) {
                    hospByLoc.put(h.getLocation().getId(), h);
                }
            }

            // Map ambulances to zones
            Map<String, List<String>> ambByLoc = new HashMap<>();
            for (Ambulance a : ambulanceManager.getAllAmbulances()) {
                if (a.getCurrentLocation() != null) {
                    ambByLoc.computeIfAbsent(a.getCurrentLocation().getId(), k -> new ArrayList<>()).add(a.getAmbulanceId());
                }
            }

            StringBuilder sb = new StringBuilder("{");

            // Zones
            sb.append("\"zones\":[");
            List<Location> sortedLocs = new ArrayList<>(roadGraph.getLocations());
            sortedLocs.sort(Comparator.comparing(Location::getId));
            for (int i = 0; i < sortedLocs.size(); i++) {
                if (i > 0) sb.append(",");
                Location loc = sortedLocs.get(i);
                Hospital h = hospByLoc.get(loc.getId());
                List<String> ambs = ambByLoc.getOrDefault(loc.getId(), Collections.emptyList());

                StringBuilder ambArr = new StringBuilder("[");
                for (int a = 0; a < ambs.size(); a++) {
                    if (a > 0) ambArr.append(",");
                    ambArr.append("\"").append(escapeJson(ambs.get(a))).append("\"");
                }
                ambArr.append("]");

                sb.append(String.format(Locale.US,
                    "{" +
                    "\"id\":\"%s\"," +
                    "\"name\":\"%s\"," +
                    "\"x\":%d," +
                    "\"y\":%d," +
                    "\"latitude\":%.4f," +
                    "\"longitude\":%.4f," +
                    "\"isHospital\":%b," +
                    "\"hospitalName\":%s," +
                    "\"availableBeds\":%d," +
                    "\"totalBeds\":%d," +
                    "\"ambulances\":%s" +
                    "}",
                    escapeJson(loc.getId()),
                    escapeJson(loc.getName()),
                    loc.getX(),
                    loc.getY(),
                    loc.getLatitude(),
                    loc.getLongitude(),
                    h != null,
                    h != null ? ("\"" + escapeJson(h.getName()) + "\"") : "null",
                    h != null ? h.getAvailableBeds() : 0,
                    h != null ? h.getTotalBeds() : 0,
                    ambArr.toString()
                ));
            }
            sb.append("],");

            // Unique Roads
            sb.append("\"roads\":[");
            Set<String> seenEdges = new HashSet<>();
            boolean firstRoad = true;
            for (Location loc : roadGraph.getLocations()) {
                for (Road r : roadGraph.getRoads(loc)) {
                    String key = loc.getId().compareTo(r.getTo().getId()) < 0 ?
                            loc.getId() + "-" + r.getTo().getId() :
                            r.getTo().getId() + "-" + loc.getId();

                    if (seenEdges.contains(key)) continue;
                    seenEdges.add(key);

                    if (!firstRoad) sb.append(",");
                    firstRoad = false;

                    sb.append(String.format(Locale.US,
                        "{" +
                        "\"fromId\":\"%s\"," +
                        "\"fromName\":\"%s\"," +
                        "\"fromX\":%d," +
                        "\"fromY\":%d," +
                        "\"toId\":\"%s\"," +
                        "\"toName\":\"%s\"," +
                        "\"toX\":%d," +
                        "\"toY\":%d," +
                        "\"distance\":%.1f," +
                        "\"travelTime\":%.1f," +
                        "\"isClosed\":%b" +
                        "}",
                        escapeJson(loc.getId()),
                        escapeJson(loc.getName()),
                        loc.getX(),
                        loc.getY(),
                        escapeJson(r.getTo().getId()),
                        escapeJson(r.getTo().getName()),
                        r.getTo().getX(),
                        r.getTo().getY(),
                        r.getDistance(),
                        r.getTravelTime(),
                        r.isClosed()
                    ));
                }
            }
            sb.append("]");
            sb.append("}");

            sendResponse(exchange, 200, "application/json", sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * GET /api/dijkstra/trace?from=<zone>&to=<zone>:
     * Runs Dijkstra's Shortest Path Algorithm on the live RoadGraph and returns an ordered
     * step-by-step audit trace of every step, priority queue state, distance table, and relaxation.
     */
    private class DijkstraTraceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method Not Allowed\"}".getBytes());
                return;
            }

            Map<String, String> qparams = parseQueryParams(exchange);
            String fromParam = qparams.get("from");
            String toParam = qparams.get("to");

            if (fromParam == null || fromParam.trim().isEmpty() || toParam == null || toParam.trim().isEmpty()) {
                sendResponse(exchange, 400, "application/json",
                        "{\"error\":\"Missing parameters: 'from' and 'to' zone IDs or names are required (e.g. /api/dijkstra/trace?from=L1&to=L8)\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }

            Location fromLoc = roadGraph.getLocationById(fromParam.trim());
            if (fromLoc == null) fromLoc = roadGraph.getLocationByName(fromParam.trim());

            Location toLoc = roadGraph.getLocationById(toParam.trim());
            if (toLoc == null) toLoc = roadGraph.getLocationByName(toParam.trim());

            if (fromLoc == null || toLoc == null) {
                String missing = fromLoc == null ? fromParam : toParam;
                sendResponse(exchange, 400, "application/json",
                        String.format("{\"error\":\"Zone not found: '%s'. Valid zone IDs are L1 through L12.\"}", escapeJson(missing)).getBytes(StandardCharsets.UTF_8));
                return;
            }

            model.DijkstraTrace trace;
            synchronized (dispatchLock) {
                trace = routePlanner.traceRoute(fromLoc, toLoc);
            }

            String json = trace.toJson();
            sendResponse(exchange, 200, "application/json", json.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Updates road closure or traffic delay.
     */
    private class RoadUpdateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method Not Allowed\"}".getBytes());
                return;
            }

            Map<String, String> params = parseRequestBody(exchange);
            String fromLocStr = params.get("fromLoc");
            String toLocStr = params.get("toLoc");
            String roadAction = params.get("roadAction"); // OPEN or CLOSED
            String delayStr = params.get("delayMinutes");

            Location loc1 = resolveLocation(fromLocStr);
            Location loc2 = resolveLocation(toLocStr);

            if (loc1 == null || loc2 == null) {
                sendResponse(exchange, 400, "application/json", "{\"success\":false,\"error\":\"Invalid location specified\"}".getBytes());
                return;
            }

            boolean isClosed = "CLOSED".equalsIgnoreCase(roadAction);

            synchronized (dispatchLock) {
                roadGraph.setRoadClosed(loc1, loc2, isClosed);

                if (delayStr != null && !delayStr.isEmpty()) {
                    try {
                        double delay = Double.parseDouble(delayStr);
                        if (delay > 0) {
                            roadGraph.updateTravelTime(loc1, loc2, delay);
                        }
                    } catch (NumberFormatException ignored) {}
                }

                // Persist road modification to SQL database
                Road r = roadGraph.findRoad(loc1, loc2);
                double travelTime = (r != null) ? r.getTravelTime() : 10.0;
                roadDao.updateRoad(loc1.getId(), loc2.getId(), travelTime, isClosed);
            }

            // Immediately trigger auto-dispatch (road state might unblock ambulances)
            autoDispatcher.trigger();

            String response = String.format(Locale.US,
                "{\"success\":true,\"message\":\"Road between %s and %s updated.\",\"isClosed\":%b}",
                escapeJson(loc1.getName()), escapeJson(loc2.getName()), isClosed);

            sendResponse(exchange, 200, "application/json", response.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Takes an emergency call, triages it, and inserts it into the PriorityQueue.
     * Immediately triggers AutoDispatcher for autonomous ambulance assignment.
     */
    private class IntakeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method Not Allowed\"}".getBytes());
                return;
            }

            Map<String, String> params = parseRequestBody(exchange);
            String patientName = params.get("patientName");
            String locationStr = params.get("emergencyLocation");
            String conditionStr = params.getOrDefault("condition", "GENERAL_ILLNESS");
            String severityStr = params.getOrDefault("severityTier", "CRITICAL");
            String capabilityStr = params.get("requiredCapability");

            // Strict validation: Reject missing or empty required fields with 400 Bad Request
            if (patientName == null || patientName.trim().isEmpty() || locationStr == null || locationStr.trim().isEmpty()) {
                sendResponse(exchange, 400, "application/json",
                    "{\"success\":false,\"error\":\"Validation Error: 'patientName' and 'emergencyLocation' are mandatory fields.\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }

            patientName = patientName.trim();
            Location loc = resolveLocation(locationStr);
            if (loc == null) {
                sendResponse(exchange, 400, "application/json",
                    "{\"success\":false,\"error\":\"Validation Error: Invalid emergency location specified.\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }

            Severity severity;
            try {
                severity = Severity.valueOf(severityStr.toUpperCase().trim());
            } catch (Exception e) {
                severity = Severity.CRITICAL;
            }

            Condition condition;
            try {
                condition = Condition.valueOf(conditionStr.toUpperCase().trim());
            } catch (Exception e) {
                condition = Condition.GENERAL_ILLNESS;
            }

            Capability capability;
            if (capabilityStr != null && !capabilityStr.isEmpty() && !capabilityStr.equalsIgnoreCase("AUTO")) {
                try {
                    capability = Capability.valueOf(capabilityStr.toUpperCase().trim());
                } catch (Exception e) {
                    capability = condition.getDefaultRequiredCapability();
                }
            } else {
                capability = condition.getDefaultRequiredCapability();
            }

            EmergencyRequest req;
            int queueSize;
            synchronized (dispatchLock) {
                String reqId = "REQ-" + (requestCounter++);
                req = new EmergencyRequest(reqId, patientName, loc, severity, condition, capability);
                dispatchManager.addEmergencyRequest(req);
                emergencyDao.insert(req);
                queueSize = dispatchManager.getPendingQueueSize();
            }

            // Immediately trigger AutoDispatcher to dispatch ambulance without delay!
            autoDispatcher.trigger();

            String response = String.format(Locale.US,
                "{" +
                "\"success\":true," +
                "\"requestId\":\"%s\"," +
                "\"patientName\":\"%s\"," +
                "\"location\":\"%s\"," +
                "\"severity\":\"%s\"," +
                "\"condition\":\"%s\"," +
                "\"capability\":\"%s\"," +
                "\"queuePosition\":%d," +
                "\"message\":\"Emergency call recorded and queued for autonomous dispatch\"" +
                "}",
                escapeJson(req.getRequestId()),
                escapeJson(req.getPatientName()),
                escapeJson(loc.getName()),
                escapeJson(severity.name()),
                escapeJson(condition.name()),
                escapeJson(capability.name()),
                queueSize
            );

            sendResponse(exchange, 200, "application/json", response.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Executes manual override emergency dispatch.
     */
    private class DispatchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method Not Allowed\"}".getBytes());
                return;
            }

            synchronized (dispatchLock) {
                if (!dispatchManager.hasPendingRequests()) {
                    sendResponse(exchange, 400, "application/json",
                        "{\"success\":false,\"error\":\"No pending emergency requests in queue\"}".getBytes());
                    return;
                }

                autoDispatcher.runDispatchCycle();

                String response = "{\"success\":true,\"message\":\"Manual dispatch cycle processed successfully.\"}";
                sendResponse(exchange, 200, "application/json", response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * Completes trip for an ambulance and admits the patient.
     * Persists completion into database trips ledger, and triggers next auto-dispatch.
     */
    private class CompleteTripHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method Not Allowed\"}".getBytes());
                return;
            }

            Map<String, String> params = parseRequestBody(exchange);
            String ambId = params.get("ambulanceId");

            Ambulance amb = null;
            if (ambId != null && !ambId.isEmpty()) {
                amb = ambulanceManager.getAmbulanceById(ambId);
            }

            if (amb == null) {
                List<Ambulance> busy = ambulanceManager.getBusyAmbulances();
                if (!busy.isEmpty()) {
                    amb = busy.get(0);
                }
            }

            if (amb == null || amb.isAvailable()) {
                sendResponse(exchange, 400, "application/json",
                    "{\"success\":false,\"error\":\"No active busy ambulance found to complete trip.\"}".getBytes());
                return;
            }

            synchronized (dispatchLock) {
                autoDispatcher.completeTripManually(amb);
            }

            // Freeing an ambulance immediately triggers next auto-dispatch!
            autoDispatcher.trigger();

            String response = String.format(Locale.US,
                "{\"success\":true,\"ambulanceId\":\"%s\",\"message\":\"Trip completed. Patient admitted to hospital.\"}",
                escapeJson(amb.getAmbulanceId())
            );

            sendResponse(exchange, 200, "application/json", response.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Returns resolved trip history directly from SQL database.
     */
    private class HistoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method Not Allowed\"}".getBytes());
                return;
            }

            List<TripDao.TripRecord> list = tripDao.getAllTrips();
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                TripDao.TripRecord r = list.get(i);
                if (i > 0) sb.append(",");

                sb.append(String.format(Locale.US,
                    "{" +
                    "\"requestId\":\"%s\"," +
                    "\"patientName\":\"%s\"," +
                    "\"location\":\"%s\"," +
                    "\"severity\":\"%s\"," +
                    "\"ambulanceId\":\"%s\"," +
                    "\"hospitalName\":\"%s\"," +
                    "\"timestamp\":\"%s\"," +
                    "\"status\":\"%s\"" +
                    "}",
                    escapeJson(r.getRequestId()),
                    escapeJson(r.getPatientName()),
                    escapeJson(r.getLocationName()),
                    escapeJson(r.getSeverity()),
                    escapeJson(r.getAmbulanceId()),
                    escapeJson(r.getHospitalName()),
                    escapeJson(r.getTimestamp()),
                    escapeJson(r.getStatus())
                ));
            }
            sb.append("]");

            sendResponse(exchange, 200, "application/json", sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private void seedInitialData() {
        List<EmergencyRequest> samples = DataInitializer.getSampleEmergencyRequests(roadGraph);
        for (EmergencyRequest req : samples) {
            dispatchManager.addEmergencyRequest(req);
            emergencyDao.insert(req);
        }
        System.out.printf("[WebServer] Sample batch of %d emergencies queued for testing.%n", samples.size());
    }

    /**
     * Re-seeds 15 sample requests.
     */
    private class SeedSamplesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method Not Allowed\"}".getBytes());
                return;
            }

            synchronized (dispatchLock) {
                seedInitialData();
            }
            autoDispatcher.trigger();

            sendResponse(exchange, 200, "application/json",
                ("{\"success\":true,\"queueSize\":" + dispatchManager.getPendingQueueSize() + "}").getBytes());
        }
    }

    /**
     * Returns current status of the background AutoDispatcher engine.
     */
    private class AutoDispatchStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method Not Allowed\"}".getBytes());
                return;
            }

            synchronized (dispatchLock) {
                String json = String.format(Locale.US,
                    "{" +
                    "\"autoDispatchEnabled\":%b," +
                    "\"preemptionEnabled\":%b," +
                    "\"activeSimulations\":%d," +
                    "\"pendingQueue\":%d," +
                    "\"availableAmbulances\":%d" +
                    "}",
                    autoDispatcher.isAutoDispatchEnabled(),
                    autoDispatcher.isPreemptionEnabled(),
                    autoDispatcher.getActiveSimulationsCount(),
                    dispatchManager.getPendingQueueSize(),
                    ambulanceManager.getAvailableAmbulances().size()
                );
                sendResponse(exchange, 200, "application/json", json.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * Toggles the background AutoDispatcher engine on or off.
     */
    private class AutoDispatchToggleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method Not Allowed\"}".getBytes());
                return;
            }

            Map<String, String> params = parseRequestBody(exchange);
            boolean newState;
            if (params.containsKey("enabled")) {
                newState = Boolean.parseBoolean(params.get("enabled"));
            } else {
                newState = !autoDispatcher.isAutoDispatchEnabled();
            }

            autoDispatcher.setAutoDispatchEnabled(newState);
            String msg = newState ? "Autonomous dispatching activated" : "Autonomous dispatching paused (manual mode)";

            String json = String.format(Locale.US,
                "{\"success\":true,\"autoDispatchEnabled\":%b,\"message\":\"%s\"}",
                newState, escapeJson(msg)
            );
            sendResponse(exchange, 200, "application/json", json.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * POST /api/reset: Clears runtime data (emergencies, trips) and resets master data
     * (fleet to base stations, hospital beds to full capacity).
     */
    private class ResetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method Not Allowed\"}".getBytes());
                return;
            }

            synchronized (dispatchLock) {
                setDemoMode(false);
                autoDispatcher.cancelAllSimulations();
                dispatchManager.clearAll();
                emergencyDao.clearAllEmergencies();
                tripDao.clearAllTrips();
                ambulanceManager.resetFleet(roadGraph);
                ambulanceDao.resetFleet();
                hospitalManager.resetAllBeds();
                hospitalDao.resetAllBeds();
                requestCounter = 1;
            }

            System.out.println("[WebServer] System reset completed: all runtime queues, active trips, and history cleared.");
            String json = "{\"success\":true,\"message\":\"System reset: All runtime emergencies and trips cleared. Master fleet and hospital beds restored to 100% capacity.\"}";
            sendResponse(exchange, 200, "application/json", json.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * POST /api/demo/toggle: Toggles random emergency call simulation generator.
     */
    private class DemoToggleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method Not Allowed\"}".getBytes());
                return;
            }

            setDemoMode(!demoModeEnabled);
            String json = String.format(Locale.US,
                "{\"success\":true,\"demoMode\":%b,\"message\":\"Simulation Demo Mode is now %s.\"}",
                demoModeEnabled, demoModeEnabled ? "ACTIVE (generating calls)" : "OFF"
            );
            sendResponse(exchange, 200, "application/json", json.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * GET /api/demo/status: Returns current simulation demo mode status.
     */
    private class DemoStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String json = String.format(Locale.US, "{\"demoMode\":%b}", demoModeEnabled);
            sendResponse(exchange, 200, "application/json", json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private synchronized void setDemoMode(boolean enable) {
        this.demoModeEnabled = enable;
        if (enable && (demoFuture == null || demoFuture.isCancelled())) {
            demoFuture = demoExecutor.scheduleWithFixedDelay(this::generateDemoEmergency, 1, 4, TimeUnit.SECONDS);
            System.out.println("[WebServer] Demo mode started: generating randomized emergency calls every 4s.");
        } else if (!enable && demoFuture != null) {
            demoFuture.cancel(false);
            demoFuture = null;
            System.out.println("[WebServer] Demo mode stopped.");
        }
    }

    private void generateDemoEmergency() {
        try {
            String[] names = {
                "Arun Kumar", "Lakshmi Sundaram", "Joseph Daniel", "Fatima Begum",
                "Ramesh Patel", "Ananya Verma", "Kiran Shah", "Sunita Rao",
                "Venkatesh Iyer", "Meera Sen", "David Fernandes", "Pooja Malhotra"
            };
            String randomName = names[(int)(Math.random() * names.length)];
            List<Location> locs = new ArrayList<>(roadGraph.getLocations());
            if (locs.isEmpty()) return;
            Location randomLoc = locs.get((int)(Math.random() * locs.size()));

            Condition[] conditions = Condition.values();
            Condition randomCond = conditions[(int)(Math.random() * conditions.length)];
            Severity[] severities = Severity.values();
            Severity randomSev = severities[(int)(Math.random() * severities.length)];
            Capability requiredCap = randomCond.getDefaultRequiredCapability();

            synchronized (dispatchLock) {
                String reqId = "REQ-" + (requestCounter++);
                EmergencyRequest req = new EmergencyRequest(reqId, randomName, randomLoc, randomSev, randomCond, requiredCap);
                dispatchManager.addEmergencyRequest(req);
                emergencyDao.insert(req);
            }
            autoDispatcher.trigger();
        } catch (Exception e) {
            System.err.println("[DemoMode] Simulation generator error: " + e.getMessage());
        }
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private Location resolveLocation(String locStr) {
        if (locStr == null || locStr.trim().isEmpty()) return null;
        Location loc = roadGraph.getLocationById(locStr.trim());
        if (loc == null) {
            loc = roadGraph.getLocationByName(locStr.trim());
        }
        return loc;
    }

    private Map<String, String> parseRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        Map<String, String> map = new HashMap<>();

        if (body.isEmpty()) return map;

        // Check if JSON
        if (body.startsWith("{") && body.endsWith("}")) {
            String content = body.substring(1, body.length() - 1);
            String[] pairs = content.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            for (String pair : pairs) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    String k = kv[0].trim().replaceAll("^\"|\"$", "");
                    String v = kv[1].trim().replaceAll("^\"|\"$", "");
                    map.put(k, v);
                }
            }
            return map;
        }

        // Form URL-encoded
        String[] pairs = body.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                String v = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                map.put(k, v);
            }
        }
        return map;
    }

    private Map<String, String> parseQueryParams(HttpExchange exchange) {
        Map<String, String> map = new HashMap<>();
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.trim().isEmpty()) return map;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                String v = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                map.put(k, v);
            } else if (kv.length == 1) {
                String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                map.put(k, "");
            }
        }
        return map;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String contentType, byte[] data) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(statusCode, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private String getContentType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".html")) return "text/html; charset=UTF-8";
        if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
