import algorithm.RoutePlanner;
import graph.RoadGraph;
import model.Ambulance;
import model.EmergencyRequest;
import model.Location;
import model.Severity;
import service.AmbulanceManager;
import service.DispatchManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {

    private static int requestCounter = 1;

    public static void main(String[] args) {
        // 1. Initialize Road Graph & Locations
        RoadGraph graph = new RoadGraph();

        Location hospital = new Location("L1", "Central Hospital", 12.9716, 77.5946);
        Location station = new Location("L2", "City Station", 12.9750, 77.6000);
        Location mall = new Location("L3", "Metro Mall", 12.9800, 77.6100);
        Location airport = new Location("L4", "Airport Terminal", 13.0000, 77.7000);

        graph.addBidirectionalRoad(hospital, station, 3.5, 10.0);
        graph.addBidirectionalRoad(station, mall, 4.0, 12.0);
        graph.addBidirectionalRoad(hospital, mall, 9.0, 25.0);
        graph.addBidirectionalRoad(mall, airport, 15.0, 30.0);

        List<Location> locationList = new ArrayList<>();
        locationList.add(hospital);
        locationList.add(station);
        locationList.add(mall);
        locationList.add(airport);

        // 2. Initialize Route Planner & Ambulance Fleet
        RoutePlanner planner = new RoutePlanner(graph);
        AmbulanceManager ambulanceManager = new AmbulanceManager();

        ambulanceManager.addAmbulance(new Ambulance("A1", station, true));
        ambulanceManager.addAmbulance(new Ambulance("A2", airport, true));
        ambulanceManager.addAmbulance(new Ambulance("A3", hospital, true));

        // 3. Initialize Dispatch Manager
        DispatchManager dispatchManager = new DispatchManager(ambulanceManager, planner);

        try (Scanner scanner = new Scanner(System.in)) {
            boolean running = true;

            while (running) {
                printMainMenu();
                int choice = readIntInput(scanner, "Enter choice: ");

                switch (choice) {
                    case 1 -> createEmergencyRequest(scanner, locationList, dispatchManager);
                    case 2 -> viewPendingRequests(dispatchManager);
                    case 3 -> dispatchManager.dispatchNextEmergency();
                    case 4 -> viewAmbulanceStatus(ambulanceManager);
                    case 5 -> completeAmbulanceTrip(scanner, ambulanceManager);
                    case 6 -> {
                        System.out.println("\nExiting Emergency Ambulance Dispatch System. Stay safe!");
                        running = false;
                    }
                    default -> System.out.println("\n[!] Invalid choice. Please select an option between 1 and 6.");
                }
            }
        }
    }

    private static void printMainMenu() {
        System.out.println("\n========================================");
        System.out.println(" EMERGENCY AMBULANCE DISPATCH SYSTEM");
        System.out.println("========================================");
        System.out.println("1. Create Emergency Request");
        System.out.println("2. View Pending Requests");
        System.out.println("3. Dispatch Next Emergency");
        System.out.println("4. View Ambulance Status");
        System.out.println("5. Complete Ambulance Trip");
        System.out.println("6. Exit");
        System.out.println();
    }

    private static void createEmergencyRequest(Scanner scanner, List<Location> locationList, DispatchManager dispatchManager) {
        System.out.println("\n========================================");
        System.out.println("Enter Emergency Details");
        System.out.println("========================================");

        System.out.print("Name : ");
        String name = scanner.nextLine().trim();
        while (name.isEmpty()) {
            System.out.print("Name cannot be empty. Enter Name: ");
            name = scanner.nextLine().trim();
        }

        System.out.println("\nAvailable Locations:");
        for (int i = 0; i < locationList.size(); i++) {
            System.out.println((i + 1) + ". " + locationList.get(i).getName());
        }

        int locChoice = readIntInput(scanner, "\nSelect Emergency Location: ");
        while (locChoice < 1 || locChoice > locationList.size()) {
            System.out.println("[!] Invalid selection. Please enter a number between 1 and " + locationList.size());
            locChoice = readIntInput(scanner, "Select Emergency Location: ");
        }
        Location selectedLocation = locationList.get(locChoice - 1);

        System.out.println("\nSeverity:");
        System.out.println("1. Critical");
        System.out.println("2. Serious");
        System.out.println("3. Minor");

        int sevChoice = readIntInput(scanner, "\nSelect Severity: ");
        while (sevChoice < 1 || sevChoice > 3) {
            System.out.println("[!] Invalid severity choice. Please select 1, 2, or 3.");
            sevChoice = readIntInput(scanner, "Select Severity: ");
        }

        Severity severity = switch (sevChoice) {
            case 1 -> Severity.CRITICAL;
            case 2 -> Severity.SERIOUS;
            default -> Severity.MINOR;
        };

        String reqId = "REQ-" + (requestCounter++);
        EmergencyRequest request = new EmergencyRequest(reqId, name, selectedLocation, severity);
        dispatchManager.addEmergencyRequest(request);
    }

    private static void viewPendingRequests(DispatchManager dispatchManager) {
        System.out.println("\n========================================");
        System.out.println("          PENDING EMERGENCIES");
        System.out.println("========================================");

        List<EmergencyRequest> pending = dispatchManager.getPendingRequests();
        if (pending.isEmpty()) {
            System.out.println("\nNo pending emergency requests in queue.");
        } else {
            for (int i = 0; i < pending.size(); i++) {
                EmergencyRequest req = pending.get(i);
                System.out.println("\n" + (i + 1) + ". " + req.getPatientName());
                System.out.println("   Location : " + req.getEmergencyLocation().getName());
                System.out.println("   Severity : " + req.getSeverity());
            }
        }
    }

    private static void viewAmbulanceStatus(AmbulanceManager ambulanceManager) {
        System.out.println("\n========================================");
        System.out.println("         AMBULANCE FLEET STATUS");
        System.out.println("========================================");

        List<Ambulance> list = ambulanceManager.getAllAmbulances();
        for (Ambulance amb : list) {
            System.out.println(amb.toString());
        }
    }

    private static void completeAmbulanceTrip(Scanner scanner, AmbulanceManager ambulanceManager) {
        System.out.println("\n========================================");
        System.out.println("       COMPLETE AMBULANCE TRIP");
        System.out.println("========================================");

        List<Ambulance> busyList = ambulanceManager.getBusyAmbulances();
        if (busyList.isEmpty()) {
            System.out.println("\nNo ambulances are currently busy.");
            return;
        }

        System.out.println("\nBusy Ambulances:\n");
        for (int i = 0; i < busyList.size(); i++) {
            Ambulance amb = busyList.get(i);
            System.out.println((i + 1) + ". " + amb.getAmbulanceId() + " | Current Location: " + amb.getCurrentLocation().getName());
        }

        int choice = readIntInput(scanner, "\nSelect ambulance: ");
        while (choice < 1 || choice > busyList.size()) {
            System.out.println("[!] Invalid selection. Please select a number between 1 and " + busyList.size());
            choice = readIntInput(scanner, "Select ambulance: ");
        }

        Ambulance selected = busyList.get(choice - 1);
        ambulanceManager.completeTrip(selected);

        System.out.println("\nTrip completed successfully.\n");
        System.out.println(selected.getAmbulanceId() + ":");
        System.out.println("Location : " + selected.getCurrentLocation().getName());
        System.out.println("Status   : " + selected.getStatusString());
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
