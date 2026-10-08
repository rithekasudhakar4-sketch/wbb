package service;

import algorithm.RoutePlanner;
import graph.RoadGraph;
import model.Capability;
import model.Hospital;
import model.Location;
import model.RouteResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Manages hospital database, capability matching, bed capacities,
 * and nearest-suitable hospital selection.
 */
public class HospitalManager {

    private final List<Hospital> hospitals;
    private final Map<String, Hospital> hospitalById;

    public HospitalManager() {
        this.hospitals = new ArrayList<>();
        this.hospitalById = new HashMap<>();
    }

    public void addHospital(Hospital hospital) {
        if (hospital == null) return;
        hospitals.add(hospital);
        hospitalById.put(hospital.getId(), hospital);
    }

    public List<Hospital> getAllHospitals() {
        return Collections.unmodifiableList(hospitals);
    }

    public Hospital getHospitalById(String id) {
        return hospitalById.get(id);
    }

    /**
     * Loads hospitals from a text file or initializes realistic defaults if file is missing.
     */
    public void loadHospitalsFromFileOrDefault(String filePath, RoadGraph graph) {
        File file = new File(filePath);
        boolean loaded = false;

        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;

                    String[] parts = line.split(",");
                    if (parts.length >= 6) {
                        String id = parts[0].trim();
                        String name = parts[1].trim();
                        String locName = parts[2].trim();
                        String capsStr = parts[3].trim();
                        int totalBeds = Integer.parseInt(parts[4].trim());
                        int availBeds = Integer.parseInt(parts[5].trim());

                        Location loc = graph.getLocationByName(locName);
                        if (loc == null) {
                            loc = new Location("LOC-" + id, locName, 12.9 + (hospitals.size() * 0.02), 77.5 + (hospitals.size() * 0.02));
                            graph.addLocation(loc);
                        }

                        Set<Capability> capSet = new HashSet<>();
                        for (String c : capsStr.split(";")) {
                            try {
                                capSet.add(Capability.valueOf(c.trim().toUpperCase()));
                            } catch (IllegalArgumentException ignored) {
                            }
                        }

                        Hospital h = new Hospital(id, name, loc, capSet, totalBeds, availBeds);
                        addHospital(h);
                        loaded = true;
                    }
                }
            } catch (IOException | NumberFormatException e) {
                System.out.println("[Notice] Error reading " + filePath + ", defaulting to built-in hospital registry.");
            }
        }

        if (!loaded || hospitals.isEmpty()) {
            seedDefaultHospitals(graph);
        }
    }

    private void seedDefaultHospitals(RoadGraph graph) {
        Location h1Loc = graph.getLocationByName("Central Hospital Junction");
        Location h2Loc = graph.getLocationByName("Industrial Area");
        Location h3Loc = graph.getLocationByName("Residential Colony A");
        Location h4Loc = graph.getLocationByName("College Road");
        Location h5Loc = graph.getLocationByName("Market Circle");

        addHospital(new Hospital("H1", "Nalam General Hospital",
                h1Loc != null ? h1Loc : new Location("L1", "Central Hospital Junction", 12.9716, 77.5946),
                Set.of(Capability.ICU, Capability.GENERAL_WARD, Capability.TRAUMA_CENTRE), 25, 12));

        addHospital(new Hospital("H2", "Apex Heart & Trauma Institute",
                h2Loc != null ? h2Loc : new Location("L2", "Industrial Area", 12.9550, 77.5800),
                Set.of(Capability.CARDIAC_CARE, Capability.TRAUMA_CENTRE, Capability.ICU), 15, 4));

        addHospital(new Hospital("H3", "Grace Children & Multi-Care",
                h3Loc != null ? h3Loc : new Location("L3", "Residential Colony A", 12.9800, 77.6100),
                Set.of(Capability.PEDIATRIC_ICU, Capability.GENERAL_WARD, Capability.ICU), 12, 3));

        addHospital(new Hospital("H4", "Sanjeevani Burn & Critical Care",
                h4Loc != null ? h4Loc : new Location("L4", "College Road", 12.9900, 77.6300),
                Set.of(Capability.BURN_UNIT, Capability.ICU, Capability.TRAUMA_CENTRE), 10, 2));

        addHospital(new Hospital("H5", "Nalam Neuro & Stroke Care",
                h5Loc != null ? h5Loc : new Location("L5", "Market Circle", 12.9650, 77.6050),
                Set.of(Capability.STROKE_UNIT, Capability.ICU, Capability.CARDIAC_CARE), 18, 6));
    }

    /**
     * Evaluates hospitals for capability and bed availability, calculates Dijkstra ETA,
     * and produces an explainable decision output.
     */
    public HospitalSelectionResult evaluateAndSelectHospital(Location patientLoc, Capability requiredCap, RoutePlanner planner) {
        System.out.println("---------------------------------------------------------------");
        System.out.printf("Checking hospitals for capability: %s (%s)...%n",
                requiredCap.name(), requiredCap.getDisplayName());
        System.out.println("---------------------------------------------------------------");

        Hospital bestHospital = null;
        RouteResult bestRoute = null;
        double minTravelTime = Double.MAX_VALUE;

        for (Hospital h : hospitals) {
            boolean hasCap = h.hasCapability(requiredCap);
            boolean hasBeds = h.hasAvailableBed();

            if (!hasCap) {
                System.out.printf("  [-] %-32s (%s) -- no %s [NO UNIT]%n",
                        h.getName(), h.getLocation().getName(), requiredCap.name());
                continue;
            }

            if (!hasBeds) {
                System.out.printf("  [!] %-32s (%s) -- has %s, but BEDS FULL (0/%d) [FULL]%n",
                        h.getName(), h.getLocation().getName(), requiredCap.name(), h.getTotalBeds());
                continue;
            }

            RouteResult route = planner.planRoute(patientLoc, h.getLocation());
            if (!route.isReachable()) {
                System.out.printf("  [!] %-32s (%s) -- has %s & beds, but UNREACHABLE due to road closures [BLOCKED]%n",
                        h.getName(), h.getLocation().getName(), requiredCap.name());
                continue;
            }

            System.out.printf("  [+] %-32s (%s) -- has %s [MATCH] (%.1f km / %.1f min) [Beds: %d/%d]%n",
                    h.getName(), h.getLocation().getName(), requiredCap.name(),
                    route.getTotalDistance(), route.getTotalTime(),
                    h.getAvailableBeds(), h.getTotalBeds());

            if (route.getTotalTime() < minTravelTime) {
                minTravelTime = route.getTotalTime();
                bestHospital = h;
                bestRoute = route;
            }
        }

        if (bestHospital != null) {
            System.out.println("---------------------------------------------------------------");
            System.out.printf(">> Chosen Hospital : %s (%s)%n", bestHospital.getName(), bestHospital.getLocation().getName());
            System.out.printf(">> Route to Hospital: %s%n", bestRoute.getFormattedPath());
            System.out.printf(">> Patient-to-Hospital ETA: %.1f minutes (%.1f km)%n", bestRoute.getTotalTime(), bestRoute.getTotalDistance());
            System.out.println("---------------------------------------------------------------");
        } else {
            System.out.println("---------------------------------------------------------------");
            System.out.println(">> [ALERT] No suitable hospital found with required capability and available beds!");
            System.out.println("---------------------------------------------------------------");
        }

        return new HospitalSelectionResult(bestHospital, bestRoute);
    }

    public void displayHospitals() {
        System.out.println("\n=========================================================================================");
        System.out.println("                            NALAM NAGAR HOSPITAL REGISTRY");
        System.out.println("=========================================================================================");
        System.out.printf("%-5s | %-32s | %-25s | %-9s | %s%n",
                "ID", "Hospital Name", "Location", "Beds", "Capabilities");
        System.out.println("-----------------------------------------------------------------------------------------");
        for (Hospital h : hospitals) {
            StringBuilder caps = new StringBuilder();
            for (Capability c : h.getCapabilities()) {
                if (caps.length() > 0) caps.append(", ");
                caps.append(c.name());
            }
            System.out.printf("%-5s | %-32s | %-25s | %2d/%-5d | %s%n",
                    h.getId(), h.getName(), h.getLocation().getName(),
                    h.getAvailableBeds(), h.getTotalBeds(), caps.toString());
        }
        System.out.println("=========================================================================================\n");
    }

    public static class HospitalSelectionResult {
        private final Hospital hospital;
        private final RouteResult route;

        public HospitalSelectionResult(Hospital hospital, RouteResult route) {
            this.hospital = hospital;
            this.route = route;
        }

        public Hospital getHospital() {
            return hospital;
        }

        public RouteResult getRoute() {
            return route;
        }

        public boolean isSuccess() {
            return hospital != null && route != null && route.isReachable();
        }
    }
}
