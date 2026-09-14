import algorithm.RoutePlanner;
import graph.RoadGraph;
import model.Location;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        System.out.println("Initializing Road Graph...");

        RoadGraph graph = new RoadGraph();

        // Create Locations
        Location hospital = new Location("L1", "Central Hospital", 12.9716, 77.5946);
        Location station = new Location("L2", "City Station", 12.9750, 77.6000);
        Location mall = new Location("L3", "Metro Mall", 12.9800, 77.6100);
        Location airport = new Location("L4", "Airport Terminal", 13.0000, 77.7000);

        // Add Roads
        graph.addBidirectionalRoad(hospital, station, 3.5, 10.0);
        graph.addBidirectionalRoad(station, mall, 4.0, 12.0);
        graph.addBidirectionalRoad(hospital, mall, 9.0, 25.0);
        graph.addBidirectionalRoad(mall, airport, 15.0, 30.0);

        // Display Graph
        graph.displayGraph();

        // Plan Route
        System.out.println("\n========== ROUTE PLANNING ==========");
        RoutePlanner planner = new RoutePlanner(graph);
        List<Location> route = planner.findShortestRoute(hospital, airport);

        System.out.println("Shortest Route from " + hospital.getName() + " to " + airport.getName() + ":");
        for (int i = 0; i < route.size(); i++) {
            System.out.println((i + 1) + ". " + route.get(i).getName());
        }
    }
}
