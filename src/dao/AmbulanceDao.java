package dao;

import db.Database;
import graph.RoadGraph;
import model.Ambulance;
import model.AmbulanceStatus;
import model.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Ambulances.
 * Uses PreparedStatements and try-with-resources for SQL safety and clean connection management.
 */
public class AmbulanceDao {

    /**
     * Loads all ambulances from the database and maps them to Location objects in RoadGraph.
     */
    public List<Ambulance> getAll(RoadGraph graph) {
        List<Ambulance> list = new ArrayList<>();
        String sql = "SELECT id, current_location_id, is_available, status, target_location_id, total_trips FROM ambulances";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("id");
                String locId = rs.getString("current_location_id");
                boolean isAvail = rs.getBoolean("is_available");
                String statusStr = rs.getString("status");
                String targetId = rs.getString("target_location_id");
                int totalTrips = rs.getInt("total_trips");

                Location loc = graph.getLocationById(locId);
                if (loc == null) {
                    loc = graph.getLocationById("L1");
                }

                Ambulance amb = new Ambulance(id, loc, isAvail);
                try {
                    amb.setStatus(AmbulanceStatus.valueOf(statusStr));
                } catch (Exception ignored) {}

                if (targetId != null) {
                    amb.setTargetLocation(graph.getLocationById(targetId));
                }

                // Restore trip count
                for (int i = 0; i < totalTrips; i++) {
                    amb.incrementTrips();
                }

                list.add(amb);
            }
        } catch (SQLException e) {
            System.err.println("[AmbulanceDao] Error loading ambulances: " + e.getMessage());
        }
        return list;
    }

    /**
     * Updates an ambulance's status and assignment inside an existing database transaction.
     */
    public void updateStatus(Connection conn, String ambulanceId, AmbulanceStatus status,
                             boolean isAvailable, String targetLocationId) throws SQLException {
        String sql = "UPDATE ambulances SET status = ?, is_available = ?, target_location_id = ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status.name());
            stmt.setBoolean(2, isAvailable);
            stmt.setString(3, targetLocationId);
            stmt.setString(4, ambulanceId);
            stmt.executeUpdate();
        }
    }

    /**
     * Completes a mission: frees the ambulance, moves it to the hospital zone, and increments trips.
     */
    public void completeTrip(String ambulanceId, String newLocationId) {
        String sql = "UPDATE ambulances SET is_available = 1, status = 'AVAILABLE', " +
                     "current_location_id = ?, target_location_id = NULL, total_trips = total_trips + 1 " +
                     "WHERE id = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newLocationId);
            stmt.setString(2, ambulanceId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[AmbulanceDao] Error completing trip: " + e.getMessage());
        }
    }

    /**
     * Resets the entire fleet to idle status, zero trips, and original base stations.
     */
    public void resetFleet() {
        String sql = "UPDATE ambulances SET is_available = 1, status = 'AVAILABLE', target_location_id = NULL, total_trips = 0, " +
                     "current_location_id = CASE id " +
                     "  WHEN 'AMB-1' THEN 'L1' " +
                     "  WHEN 'AMB-2' THEN 'L6' " +
                     "  WHEN 'AMB-3' THEN 'L4' " +
                     "  WHEN 'AMB-4' THEN 'L12' " +
                     "  WHEN 'AMB-5' THEN 'L2' " +
                     "  ELSE current_location_id END";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[AmbulanceDao] Error resetting fleet: " + e.getMessage());
        }
    }
}
