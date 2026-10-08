import algorithm.RoutePlanner;
import graph.RoadGraph;
import model.*;
import service.*;

import java.util.List;
import java.util.Scanner;

/**
 * Main Interactive Console Application for Nalam Nagar Emergency Ambulance Dispatch & Route Optimization.
 */
public class Main {

    private static int requestCounter = 101;

    public static void main(String[] args) {
        // 1. Initialize Network Graph & Locations
        RoadGraph roadGraph = new RoadGraph();
        List<Location> locationList = DataInitializer.initializeNetwork(roadGraph);

        // 2. Initialize Route Planner
        RoutePlanner routePlanner = new RoutePlanner(roadGraph);

        // 3. Initialize Hospital Manager (from hospitals.txt or default seeds)
        HospitalManager hospitalManager = new HospitalManager();
        hospitalManager.loadHospitalsFromFileOrDefault("hospitals.txt", roadGraph);

        // 4. Initialize Ambulance Fleet
        AmbulanceManager ambulanceManager = new AmbulanceManager();
        DataInitializer.initializeAmbulances(ambulanceManager, roadGraph);

        // 5. Initialize Dispatch Coordinator
        DispatchManager dispatchManager = new DispatchManager(ambulanceManager, hospitalManager, routePlanner, roadGraph);

        System.out.println("\n=========================================================================================");
        System.out.println("     NALAM NAGAR EMERGENCY AMBULANCE DISPATCH & ROUTE OPTIMIZATION SYSTEM");
        System.out.println("=========================================================================================");
        System.out.println("System initialized successfully.");
        System.out.printf("  * %d Town Zones Loaded%n", locationList.size());
        System.out.printf("  * %d Specialized Hospitals Loaded%n", hospitalManager.getAllHospitals().size());
        System.out.printf("  * %d Emergency Ambulances Ready%n", ambulanceManager.getAllAmbulances().size());
        System.out.println("=========================================================================================");

        try (Scanner scanner = new Scanner(System.in)) {
            boolean running = true;

            while (running) {
                printMainMenu(dispatchManager.getPendingQueueSize());
                int choice = readIntInput(scanner, "Enter choice [1-10]: ");

                switch (choice) {
                    case 1 -> createEmergencyCall(scanner, locationList, dispatchManager);
                    case 2 -> viewPendingEmergencyQueue(dispatchManager);
                    case 3 -> dispatchManager.dispatchNextEmergency();
                    case 4 -> hospitalManager.displayHospitals();
                    case 5 -> ambulanceManager.displayFleet();
                    case 6 -> handleTrafficAndRoadConditions(scanner, roadGraph, locationList);
                    case 7 -> handleTripCompletion(scanner, ambulanceManager, dispatchManager);
                    case 8 -> runSampleTestScenarios(dispatchManager, roadGraph);
                    case 9 -> dispatchManager.displaySystemStatistics();
                    case 10 -> {
                        System.out.println("\nExiting Nalam Nagar Emergency Dispatch System. Stay safe!");
                        running = false;
                    }
                    default -> System.out.println("\n[!] Invalid selection. Please choose an option between 1 and 10.");
                }
            }
        }
    }

    private static void printMainMenu(int pendingCount) {
        System.out.println("\n===============================================================");
        System.out.println("                   OPERATOR CONTROL CONSOLE");
        System.out.println("===============================================================");
        System.out.println("1.  Record New Emergency Call (Interactive Triage)");
        System.out.printf("2.  View Pending Emergency Queue (%d in queue)%n", pendingCount);
        System.out.println("3.  Dispatch Highest Priority Emergency");
        System.out.println("4.  View Hospital Capacities & Medical Units");
        System.out.println("5.  View Ambulance Fleet Status");
        System.out.println("6.  Road Network & Dynamic Traffic Management");
        System.out.println("7.  Complete Ambulance Trip & Admit Patient");
        System.out.println("8.  Batch Load 15 Sample Test Emergency Cases");
        System.out.println("9.  View System Performance & Occupancy Stats");
        System.out.println("10. Exit System");
        System.out.println("===============================================================");
    }

    private static void createEmergencyCall(Scanner scanner, List<Location> locationList, DispatchManager dispatchManager) {
        System.out.println("\n===============================================================");
        System.out.println("                 NEW EMERGENCY CALL INTAKE");
        System.out.println("===============================================================");

        System.out.print("Enter Patient / Caller Name: ");
        String name = scanner.nextLine().trim();
        while (name.isEmpty()) {
            System.out.print("Name cannot be empty. Enter Name: ");
            name = scanner.nextLine().trim();
        }

        System.out.println("\nAvailable Zones / Locations in Nalam Nagar:");
        for (int i = 0; i < locationList.size(); i++) {
            System.out.printf("  %2d. %s%n", (i + 1), locationList.get(i).getName());
        }

        int locChoice = readIntInput(scanner, "\nSelect Emergency Location [1-" + locationList.size() + "]: ");
        while (locChoice < 1 || locChoice > locationList.size()) {
            System.out.println("[!] Invalid choice.");
            locChoice = readIntInput(scanner, "Select Emergency Location [1-" + locationList.size() + "]: ");
        }
        Location selectedLocation = locationList.get(locChoice - 1);

        System.out.println("\nSelect Patient Medical Condition:");
        Condition[] conditions = Condition.values();
        for (int i = 0; i < conditions.length; i++) {
            System.out.printf("  %2d. %-32s -> Recommends: %s%n",
                    (i + 1), conditions[i].getDisplayName(), conditions[i].getDefaultRequiredCapability().name());
        }

        int condChoice = readIntInput(scanner, "\nSelect Condition [1-" + conditions.length + "]: ");
        while (condChoice < 1 || condChoice > conditions.length) {
            System.out.println("[!] Invalid condition choice.");
            condChoice = readIntInput(scanner, "Select Condition [1-" + conditions.length + "]: ");
        }
        Condition selectedCondition = conditions[condChoice - 1];

        // Default severity and capability based on condition
        Severity defaultSeverity = selectedCondition.getDefaultSeverity();
        Capability defaultCapability = selectedCondition.getDefaultRequiredCapability();

        System.out.printf("%nRecommended Triage Severity: %s%n", defaultSeverity);
        System.out.println("1. Accept Recommended Severity (" + defaultSeverity + ")");
        System.out.println("2. Override Severity (CRITICAL / SERIOUS / MINOR)");
        int sevDecision = readIntInput(scanner, "Choice [1-2]: ");

        Severity finalSeverity = defaultSeverity;
        if (sevDecision == 2) {
            System.out.println("\nSelect Severity Override:");
            System.out.println("1. CRITICAL (Immediate Life-Threat)");
            System.out.println("2. SERIOUS (Urgent Intervention Needed)");
            System.out.println("3. MINOR (Non-Life-Threatening)");
            int customSev = readIntInput(scanner, "Select [1-3]: ");
            finalSeverity = switch (customSev) {
                case 1 -> Severity.CRITICAL;
                case 2 -> Severity.SERIOUS;
                default -> Severity.MINOR;
            };
        }

        String reqId = "REQ-" + (requestCounter++);
        EmergencyRequest request = new EmergencyRequest(reqId, name, selectedLocation, finalSeverity, selectedCondition, defaultCapability);
        dispatchManager.addEmergencyRequest(request);

        System.out.print("\nDo you want to dispatch an ambulance for this request immediately? (y/n): ");
        String dispatchNow = scanner.nextLine().trim().toLowerCase();
        if (dispatchNow.startsWith("y")) {
            dispatchManager.dispatchNextEmergency();
        }
    }

    private static void viewPendingEmergencyQueue(DispatchManager dispatchManager) {
        System.out.println("\n=========================================================================================");
        System.out.println("               PENDING EMERGENCY REQUESTS (PRIORITY QUEUE ORDER)");
        System.out.println("=========================================================================================");

        List<EmergencyRequest> pending = dispatchManager.getPendingRequests();
        if (pending.isEmpty()) {
            System.out.println("\nNo emergency calls are currently pending in the queue.");
        } else {
            System.out.printf("%-4s | %-8s | %-18s | %-10s | %-24s | %-20s | %s%n",
                    "Pos", "ID", "Patient Name", "Severity", "Emergency Zone", "Condition", "Required Facility");
            System.out.println("-----------------------------------------------------------------------------------------");
            for (int i = 0; i < pending.size(); i++) {
                EmergencyRequest r = pending.get(i);
                System.out.printf("%-4d | %-8s | %-18s | %-10s | %-24s | %-20s | %s%n",
                        (i + 1), r.getRequestId(), r.getPatientName(), r.getSeverity(),
                        r.getEmergencyLocation().getName(),
                        r.getCondition() != null ? r.getCondition().name() : "N/A",
                        r.getRequiredCapability() != null ? r.getRequiredCapability().name() : "N/A");
            }
        }
        System.out.println("=========================================================================================\n");
    }

    private static void handleTrafficAndRoadConditions(Scanner scanner, RoadGraph graph, List<Location> locations) {
        boolean inMenu = true;
        while (inMenu) {
            System.out.println("\n===============================================================");
            System.out.println("             ROAD NETWORK & TRAFFIC MANAGEMENT");
            System.out.println("===============================================================");
            System.out.println("1. View Complete Road Network Map");
            System.out.println("2. Report Road Closure (Accident / Construction)");
            System.out.println("3. Reopen a Closed Road");
            System.out.println("4. Update Road Travel Time (Simulate Traffic Delay)");
            System.out.println("5. Return to Main Menu");
            System.out.println("===============================================================");

            int choice = readIntInput(scanner, "Select Option [1-5]: ");
            switch (choice) {
                case 1 -> graph.displayGraph();
                case 2 -> toggleRoadStatus(scanner, graph, locations, true);
                case 3 -> toggleRoadStatus(scanner, graph, locations, false);
                case 4 -> updateTrafficDelay(scanner, graph, locations);
                case 5 -> inMenu = false;
                default -> System.out.println("[!] Invalid selection.");
            }
        }
    }

    private static void toggleRoadStatus(Scanner scanner, RoadGraph graph, List<Location> locations, boolean close) {
        System.out.println("\nSelect Location 1:");
        for (int i = 0; i < locations.size(); i++) {
            System.out.printf("  %2d. %s%n", (i + 1), locations.get(i).getName());
        }
        int c1 = readIntInput(scanner, "Select Origin Location: ");
        int c2 = readIntInput(scanner, "Select Destination Location: ");

        if (c1 < 1 || c1 > locations.size() || c2 < 1 || c2 > locations.size() || c1 == c2) {
            System.out.println("[!] Invalid location selection.");
            return;
        }

        Location loc1 = locations.get(c1 - 1);
        Location loc2 = locations.get(c2 - 1);

        boolean updated = graph.setRoadClosed(loc1, loc2, close);
        if (updated) {
            System.out.printf("%n[OK] Road between [%s] and [%s] has been marked %s.%n",
                    loc1.getName(), loc2.getName(), close ? "CLOSED (Traffic Excluded)" : "OPEN (Available)");
        } else {
            System.out.println("[!] No direct road exists between these two locations.");
        }
    }

    private static void updateTrafficDelay(Scanner scanner, RoadGraph graph, List<Location> locations) {
        System.out.println("\nSelect Road Segment to Update Traffic:");
        for (int i = 0; i < locations.size(); i++) {
            System.out.printf("  %2d. %s%n", (i + 1), locations.get(i).getName());
        }
        int c1 = readIntInput(scanner, "Select Origin Location: ");
        int c2 = readIntInput(scanner, "Select Destination Location: ");

        if (c1 < 1 || c1 > locations.size() || c2 < 1 || c2 > locations.size() || c1 == c2) {
            System.out.println("[!] Invalid selection.");
            return;
        }

        Location loc1 = locations.get(c1 - 1);
        Location loc2 = locations.get(c2 - 1);

        Road existing = graph.findRoad(loc1, loc2);
        if (existing == null) {
            System.out.println("[!] No direct road exists between these two locations.");
            return;
        }

        System.out.printf("Current travel time for %s <-> %s is %.1f minutes.%n",
                loc1.getName(), loc2.getName(), existing.getTravelTime());
        System.out.print("Enter new estimated travel time in minutes: ");
        String valStr = scanner.nextLine().trim();
        try {
            double newTime = Double.parseDouble(valStr);
            if (newTime <= 0) {
                System.out.println("[!] Travel time must be a positive number.");
                return;
            }
            graph.updateTravelTime(loc1, loc2, newTime);
            System.out.printf("[OK] Travel time updated to %.1f minutes for future route optimizations.%n", newTime);
        } catch (NumberFormatException e) {
            System.out.println("[!] Invalid numerical value.");
        }
    }

    private static void handleTripCompletion(Scanner scanner, AmbulanceManager ambulanceManager, DispatchManager dispatchManager) {
        List<Ambulance> busyAmbs = ambulanceManager.getBusyAmbulances();
        if (busyAmbs.isEmpty()) {
            System.out.println("\nNo ambulances are currently on a mission.");
            return;
        }

        System.out.println("\n===============================================================");
        System.out.println("                 COMPLETE ACTIVE AMBULANCE TRIP");
        System.out.println("===============================================================");
        for (int i = 0; i < busyAmbs.size(); i++) {
            Ambulance a = busyAmbs.get(i);
            String dest = a.getTargetLocation() != null ? a.getTargetLocation().getName() : "In Transit";
            System.out.printf("  %d. %s | Origin: %s | Target Hospital: %s%n",
                    (i + 1), a.getAmbulanceId(), a.getCurrentLocation().getName(), dest);
        }

        int choice = readIntInput(scanner, "\nSelect ambulance to mark as arrived & finished [1-" + busyAmbs.size() + "]: ");
        if (choice < 1 || choice > busyAmbs.size()) {
            System.out.println("[!] Invalid selection.");
            return;
        }

        Ambulance chosen = busyAmbs.get(choice - 1);
        dispatchManager.completeTrip(chosen);
    }

    private static void runSampleTestScenarios(DispatchManager dispatchManager, RoadGraph graph) {
        System.out.println("\n===============================================================");
        System.out.println("       LOADING 15 PRE-SEEDED TEST CASES INTO PRIORITY QUEUE");
        System.out.println("===============================================================");
        List<EmergencyRequest> samples = DataInitializer.getSampleEmergencyRequests(graph);
        for (EmergencyRequest req : samples) {
            dispatchManager.addEmergencyRequest(req);
        }
        System.out.printf("%n[OK] %d emergency calls loaded successfully.%n", samples.size());
        System.out.println("You can now view the PriorityQueue order (Option 2) or trigger dispatches (Option 3).");
    }

    private static int readIntInput(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("[!] Invalid input. Please enter a valid number.");
            }
        }
    }
}
