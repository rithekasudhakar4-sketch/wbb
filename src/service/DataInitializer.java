package service;

import graph.RoadGraph;
import model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Initializes realistic testbed data for Nalam Nagar:
 * - 12 locations / zones
 * - Interconnected bidirectional road network with travel time weights
 * - 5 ambulance fleet
 * - 5 specialized hospitals
 * - 15 comprehensive sample emergency requests for testing all scenarios
 */
public class DataInitializer {

    public static List<Location> initializeNetwork(RoadGraph graph) {
        // 1. Create Locations
        Location l1 = new Location("L1", "Central Hospital Junction", 12.9716, 77.5946);
        Location l2 = new Location("L2", "Market Circle", 12.9650, 77.6050);
        Location l3 = new Location("L3", "Residential Colony A", 12.9800, 77.6100);
        Location l4 = new Location("L4", "Industrial Area", 12.9550, 77.5800);
        Location l5 = new Location("L5", "College Road", 12.9900, 77.6300);
        Location l6 = new Location("L6", "Town Bus Terminal", 12.9750, 77.6000);
        Location l7 = new Location("L7", "Greenfield Suburb", 12.9950, 77.5900);
        Location l8 = new Location("L8", "Tech Park Zone", 12.9400, 77.6500);
        Location l9 = new Location("L9", "Old City Bazaar", 12.9600, 77.5900);
        Location l10 = new Location("L10", "Highway Bypass Junction", 12.9300, 77.6000);
        Location l11 = new Location("L11", "Riverside Gardens", 12.9850, 77.5750);
        Location l12 = new Location("L12", "North Gate Hub", 13.0100, 77.6150);

        List<Location> locations = List.of(l1, l2, l3, l4, l5, l6, l7, l8, l9, l10, l11, l12);
        for (Location loc : locations) {
            graph.addLocation(loc);
        }

        // 2. Add Roads (Distance in km, Travel Time in minutes)
        graph.addBidirectionalRoad(l1, l6, 2.5, 6.0);    // Central Hospital <-> Bus Terminal
        graph.addBidirectionalRoad(l6, l2, 3.0, 8.0);    // Bus Terminal <-> Market Circle
        graph.addBidirectionalRoad(l1, l9, 2.0, 5.0);    // Central Hospital <-> Old City
        graph.addBidirectionalRoad(l9, l4, 4.0, 10.0);   // Old City <-> Industrial Area
        graph.addBidirectionalRoad(l2, l3, 3.5, 7.0);    // Market Circle <-> Residential Colony A
        graph.addBidirectionalRoad(l3, l5, 4.0, 9.0);    // Residential Colony A <-> College Road
        graph.addBidirectionalRoad(l1, l3, 5.0, 12.0);   // Central Hospital <-> Residential Colony A
        graph.addBidirectionalRoad(l6, l7, 4.5, 11.0);   // Bus Terminal <-> Greenfield Suburb
        graph.addBidirectionalRoad(l7, l11, 3.0, 7.0);   // Greenfield <-> Riverside Gardens
        graph.addBidirectionalRoad(l11, l1, 3.8, 9.0);   // Riverside Gardens <-> Central Hospital
        graph.addBidirectionalRoad(l5, l12, 3.2, 6.5);   // College Road <-> North Gate Hub
        graph.addBidirectionalRoad(l7, l12, 4.0, 8.0);   // Greenfield <-> North Gate Hub
        graph.addBidirectionalRoad(l2, l8, 6.5, 15.0);   // Market Circle <-> Tech Park
        graph.addBidirectionalRoad(l4, l10, 4.2, 9.5);   // Industrial Area <-> Highway Bypass
        graph.addBidirectionalRoad(l10, l8, 5.0, 11.0);  // Highway Bypass <-> Tech Park
        graph.addBidirectionalRoad(l4, l2, 5.5, 14.0);   // Industrial Area <-> Market Circle

        return new ArrayList<>(locations);
    }

    public static void initializeAmbulances(AmbulanceManager ambulanceManager, RoadGraph graph) {
        Location central = graph.getLocationByName("Central Hospital Junction");
        Location busTerminal = graph.getLocationByName("Town Bus Terminal");
        Location industrial = graph.getLocationByName("Industrial Area");
        Location northGate = graph.getLocationByName("North Gate Hub");
        Location market = graph.getLocationByName("Market Circle");

        ambulanceManager.addAmbulance(new Ambulance("AMB-1", central, true));
        ambulanceManager.addAmbulance(new Ambulance("AMB-2", busTerminal, true));
        ambulanceManager.addAmbulance(new Ambulance("AMB-3", industrial, true));
        ambulanceManager.addAmbulance(new Ambulance("AMB-4", northGate, true));
        ambulanceManager.addAmbulance(new Ambulance("AMB-5", market, true));
    }

    public static List<EmergencyRequest> getSampleEmergencyRequests(RoadGraph graph) {
        List<EmergencyRequest> sampleRequests = new ArrayList<>();

        Location colA = graph.getLocationByName("Residential Colony A");
        Location colRoad = graph.getLocationByName("College Road");
        Location market = graph.getLocationByName("Market Circle");
        Location ind = graph.getLocationByName("Industrial Area");
        Location techPark = graph.getLocationByName("Tech Park Zone");
        Location oldCity = graph.getLocationByName("Old City Bazaar");
        Location riverside = graph.getLocationByName("Riverside Gardens");
        Location greenfield = graph.getLocationByName("Greenfield Suburb");
        Location northGate = graph.getLocationByName("North Gate Hub");
        Location highway = graph.getLocationByName("Highway Bypass Junction");
        Location busTerm = graph.getLocationByName("Town Bus Terminal");

        sampleRequests.add(new EmergencyRequest("REQ-101", "Ravi Sharma", colA, Severity.CRITICAL, Condition.BURN, Capability.BURN_UNIT));
        sampleRequests.add(new EmergencyRequest("REQ-102", "Priya Nair", colRoad, Severity.MINOR, Condition.GENERAL_ILLNESS, Capability.GENERAL_WARD));
        sampleRequests.add(new EmergencyRequest("REQ-103", "Anand Patel", market, Severity.CRITICAL, Condition.CARDIAC_ARREST, Capability.CARDIAC_CARE));
        sampleRequests.add(new EmergencyRequest("REQ-104", "Farooq Abdullah", ind, Severity.CRITICAL, Condition.TRAUMA, Capability.TRAUMA_CENTRE));
        sampleRequests.add(new EmergencyRequest("REQ-105", "Meera Sen", techPark, Severity.SERIOUS, Condition.STROKE, Capability.STROKE_UNIT));
        sampleRequests.add(new EmergencyRequest("REQ-106", "Aarav Gupta (Age 4)", greenfield, Severity.SERIOUS, Condition.PEDIATRIC_EMERGENCY, Capability.PEDIATRIC_ICU));
        sampleRequests.add(new EmergencyRequest("REQ-107", "Kiran Verma", oldCity, Severity.SERIOUS, Condition.RESPIRATORY_FAILURE, Capability.ICU));
        sampleRequests.add(new EmergencyRequest("REQ-108", "Deepak Rao", riverside, Severity.MINOR, Condition.FRACTURE, Capability.GENERAL_WARD));
        sampleRequests.add(new EmergencyRequest("REQ-109", "Sneha Joshi", northGate, Severity.CRITICAL, Condition.BURN, Capability.BURN_UNIT));
        sampleRequests.add(new EmergencyRequest("REQ-110", "Vikram Malhotra", highway, Severity.CRITICAL, Condition.TRAUMA, Capability.TRAUMA_CENTRE));
        sampleRequests.add(new EmergencyRequest("REQ-111", "Sunita Bai", busTerm, Severity.SERIOUS, Condition.CARDIAC_ARREST, Capability.CARDIAC_CARE));
        sampleRequests.add(new EmergencyRequest("REQ-112", "Mohit Bansal", techPark, Severity.MINOR, Condition.GENERAL_ILLNESS, Capability.GENERAL_WARD));
        sampleRequests.add(new EmergencyRequest("REQ-113", "Baby Ananya", colA, Severity.CRITICAL, Condition.PEDIATRIC_EMERGENCY, Capability.PEDIATRIC_ICU));
        sampleRequests.add(new EmergencyRequest("REQ-114", "Rajesh Khanna", ind, Severity.SERIOUS, Condition.FRACTURE, Capability.GENERAL_WARD));
        sampleRequests.add(new EmergencyRequest("REQ-115", "Tariq Mir", oldCity, Severity.CRITICAL, Condition.STROKE, Capability.STROKE_UNIT));

        return sampleRequests;
    }
}
