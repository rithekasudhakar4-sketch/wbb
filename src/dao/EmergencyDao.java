package dao;

import db.Database;
import graph.RoadGraph;
import model.Capability;
import model.Condition;
import model.EmergencyRequest;
import model.Location;
import model.Severity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Emergency requests and triage records.
 * Uses PreparedStatements for all operations to prevent SQL injection.
 */
public class EmergencyDao {

    /**
     * Saves a new emergency call intake into the database.
     * Skips insert if the request ID already exists.
     */
    public boolean insert(EmergencyRequest request) {
        String checkSql = "SELECT 1 FROM emergencies WHERE id = ?";
        String insertSql = "INSERT INTO emergencies (id, patient_name, location_id, severity, condition_name, " +
                     "required_capability, status) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = Database.getConnection()) {
            // Avoid duplicate key error if already seeded or saved
            try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
                checkPs.setString(1, request.getRequestId());
                try (ResultSet rs = checkPs.executeQuery()) {
                    if (rs.next()) {
                        return true; // Already exists
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, request.getRequestId());
                ps.setString(2, request.getPatientName());
                ps.setString(3, request.getEmergencyLocation().getId());
                ps.setString(4, request.getSeverity().name());
                ps.setString(5, request.getCondition() != null ? request.getCondition().name() : "GENERAL_ILLNESS");
                ps.setString(6, request.getRequiredCapability() != null ? request.getRequiredCapability().name() : "GENERAL_WARD");
                ps.setString(7, request.getStatus() != null ? request.getStatus() : "PENDING");

                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("[EmergencyDao] Error inserting emergency request: " + e.getMessage());
            return false;
        }
    }

    /**
     * Finds the highest numeric ID in the emergencies table (e.g. REQ-122 -> 123)
     * to avoid duplicate primary key collisions across server restarts.
     */
    public int getNextRequestIdNumber() {
        String sql = "SELECT id FROM emergencies WHERE id LIKE 'REQ-%'";
        int max = 120;
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String id = rs.getString("id");
                if (id != null && id.startsWith("REQ-")) {
                    try {
                        int num = Integer.parseInt(id.substring(4).trim());
                        if (num > max) {
                            max = num;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (SQLException e) {
            System.err.println("[EmergencyDao] Error querying max request id: " + e.getMessage());
        }
        return max + 1;
    }

    /**
     * Updates status and assignments of an emergency within a transactional connection.
     */
    public boolean updateStatus(Connection conn, String requestId, String status, String ambulanceId, String hospitalId) throws SQLException {
        String sql = "UPDATE emergencies SET status = ?, assigned_ambulance_id = ?, assigned_hospital_id = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, ambulanceId);
            ps.setString(3, hospitalId);
            ps.setString(4, requestId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Standalone status update with auto-managed connection.
     */
    public boolean updateStatus(String requestId, String status, String ambulanceId, String hospitalId) {
        try (Connection conn = Database.getConnection()) {
            return updateStatus(conn, requestId, status, ambulanceId, hospitalId);
        } catch (SQLException e) {
            System.err.println("[EmergencyDao] Error updating status: " + e.getMessage());
            return false;
        }
    }

    /**
     * Loads all currently pending emergency requests from the database
     * to populate the in-memory PriorityQueue upon server startup.
     */
    public List<EmergencyRequest> getPendingRequests(RoadGraph roadGraph) {
        List<EmergencyRequest> list = new ArrayList<>();
        String sql = "SELECT id, patient_name, location_id, severity, condition_name, required_capability " +
                     "FROM emergencies WHERE status = 'PENDING' ORDER BY created_at ASC";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("id");
                String name = rs.getString("patient_name");
                String locId = rs.getString("location_id");
                String sevStr = rs.getString("severity");
                String condStr = rs.getString("condition_name");
                String capStr = rs.getString("required_capability");

                Location loc = roadGraph.getLocationById(locId);
                if (loc == null) {
                    loc = roadGraph.getLocationByName(locId);
                }
                if (loc == null) {
                    continue;
                }

                Severity severity;
                try {
                    severity = Severity.valueOf(sevStr.toUpperCase());
                } catch (Exception e) {
                    severity = Severity.SERIOUS;
                }

                Condition condition = null;
                if (condStr != null) {
                    try { condition = Condition.valueOf(condStr.toUpperCase()); } catch (Exception ignored) {}
                }

                Capability capability = null;
                if (capStr != null) {
                    try { capability = Capability.valueOf(capStr.toUpperCase()); } catch (Exception ignored) {}
                }

                EmergencyRequest req = new EmergencyRequest(id, name, loc, severity, condition, capability);
                req.setStatus("PENDING");
                list.add(req);
            }
        } catch (SQLException e) {
            System.err.println("[EmergencyDao] Error loading pending requests: " + e.getMessage());
        }
        return list;
    }

    /**
     * Clears all emergency records from the database during system reset.
     */
    public boolean clearAllEmergencies() {
        String sql = "DELETE FROM emergencies";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            return ps.executeUpdate() >= 0;
        } catch (SQLException e) {
            System.err.println("[EmergencyDao] Error clearing emergencies: " + e.getMessage());
            return false;
        }
    }
}
