package dao;

import db.Database;
import graph.RoadGraph;
import model.Capability;
import model.Hospital;
import model.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Data Access Object for Hospitals and their medical capabilities.
 */
public class HospitalDao {

    /**
     * Loads all hospitals and their associated clinical specialties from the database.
     */
    public List<Hospital> getAll(RoadGraph graph) {
        List<Hospital> hospitals = new ArrayList<>();
        Map<String, Set<Capability>> specialtyMap = loadAllSpecialties();

        String sql = "SELECT id, name, location_id, total_beds, available_beds FROM hospitals";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("id");
                String name = rs.getString("name");
                String locId = rs.getString("location_id");
                int totalBeds = rs.getInt("total_beds");
                int availBeds = rs.getInt("available_beds");

                Location loc = graph.getLocationById(locId);
                if (loc == null) {
                    loc = graph.getLocationById("L1");
                }

                Set<Capability> caps = specialtyMap.getOrDefault(id, new HashSet<>());
                if (caps.isEmpty()) {
                    caps.add(Capability.GENERAL_WARD);
                }

                Hospital h = new Hospital(id, name, loc, caps, totalBeds, availBeds);
                hospitals.add(h);
            }
        } catch (SQLException e) {
            System.err.println("[HospitalDao] Error loading hospitals: " + e.getMessage());
        }
        return hospitals;
    }

    private Map<String, Set<Capability>> loadAllSpecialties() {
        Map<String, Set<Capability>> map = new HashMap<>();
        String sql = "SELECT hospital_id, specialty FROM hospital_specialties";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String hid = rs.getString("hospital_id");
                String spec = rs.getString("specialty");

                try {
                    Capability cap = Capability.valueOf(spec.trim().toUpperCase());
                    map.computeIfAbsent(hid, k -> new HashSet<>()).add(cap);
                } catch (Exception ignored) {}
            }
        } catch (SQLException e) {
            System.err.println("[HospitalDao] Error loading specialties: " + e.getMessage());
        }
        return map;
    }

    /**
     * Decrements/updates available beds inside an active database transaction.
     */
    public void updateAvailableBeds(Connection conn, String hospitalId, int availableBeds) throws SQLException {
        String sql = "UPDATE hospitals SET available_beds = ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, availableBeds);
            stmt.setString(2, hospitalId);
            stmt.executeUpdate();
        }
    }

    /**
     * Releases an occupied bed when a patient is discharged or handled.
     */
    public void releaseBed(String hospitalId) {
        String sql = "UPDATE hospitals SET available_beds = MIN(available_beds + 1, total_beds) WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, hospitalId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[HospitalDao] Error releasing bed: " + e.getMessage());
        }
    }

    /**
     * Resets all hospital available beds to full capacity.
     */
    public void resetAllBeds() {
        String sql = "UPDATE hospitals SET available_beds = total_beds";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[HospitalDao] Error resetting hospital beds: " + e.getMessage());
        }
    }
}
