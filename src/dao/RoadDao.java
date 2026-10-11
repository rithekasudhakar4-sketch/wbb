package dao;

import db.Database;
import graph.RoadGraph;
import model.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Data Access Object for Town Zones and Road Connections.
 */
public class RoadDao {

    /**
     * Loads all 12 zones and road edges from database into the RoadGraph.
     */
    public void loadNetworkIntoGraph(RoadGraph graph) {
        // 1. Load Locations
        String locSql = "SELECT id, name, latitude, longitude FROM locations";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(locSql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("id");
                String name = rs.getString("name");
                double lat = rs.getDouble("latitude");
                double lon = rs.getDouble("longitude");
                graph.addLocation(new Location(id, name, lat, lon));
            }
        } catch (SQLException e) {
            System.err.println("[RoadDao] Error loading locations: " + e.getMessage());
        }

        // 2. Load Road Edges
        String roadSql = "SELECT from_location_id, to_location_id, distance_km, travel_time_mins, is_closed FROM road_edges";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(roadSql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String fromId = rs.getString("from_location_id");
                String toId = rs.getString("to_location_id");
                double dist = rs.getDouble("distance_km");
                double time = rs.getDouble("travel_time_mins");
                boolean closed = rs.getBoolean("is_closed");

                Location from = graph.getLocationById(fromId);
                Location to = graph.getLocationById(toId);

                if (from != null && to != null) {
                    graph.addDirectedRoad(from, to, dist, time);
                    if (closed) {
                        graph.setRoadClosed(from, to, true);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[RoadDao] Error loading road edges: " + e.getMessage());
        }
    }

    /**
     * Updates travel time and closure status for bidirectional edges between two zones.
     */
    public void updateRoad(String fromId, String toId, double travelTimeMins, boolean isClosed) {
        String sql = "UPDATE road_edges SET travel_time_mins = ?, is_closed = ? " +
                     "WHERE (from_location_id = ? AND to_location_id = ?) " +
                     "   OR (from_location_id = ? AND to_location_id = ?)";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDouble(1, travelTimeMins);
            stmt.setBoolean(2, isClosed);
            stmt.setString(3, fromId);
            stmt.setString(4, toId);
            stmt.setString(5, toId);
            stmt.setString(6, fromId);

            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[RoadDao] Error updating road: " + e.getMessage());
        }
    }
}
