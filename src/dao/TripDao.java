package dao;

import db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for recorded trips and historical mission archives.
 * Uses PreparedStatements for all queries and updates.
 */
public class TripDao {

    /**
     * DTO representing a historical trip record for /api/history.
     */
    public static class TripRecord {
        private final String requestId;
        private final String patientName;
        private final String locationName;
        private final String severity;
        private final String ambulanceId;
        private final String hospitalName;
        private final String timestamp;
        private final String status;

        public TripRecord(String requestId, String patientName, String locationName, String severity,
                          String ambulanceId, String hospitalName, String timestamp, String status) {
            this.requestId = requestId != null ? requestId : "REQ-N/A";
            this.patientName = patientName != null ? patientName : "Anonymous";
            this.locationName = locationName != null ? locationName : "Nalam Central";
            this.severity = severity != null ? severity : "RESOLVED";
            this.ambulanceId = ambulanceId != null ? ambulanceId : "AMB-X";
            this.hospitalName = hospitalName != null ? hospitalName : "General Hospital";
            this.timestamp = timestamp != null ? timestamp : "--:--:--";
            this.status = status != null ? status : "COMPLETED";
        }

        public String getRequestId() { return requestId; }
        public String getPatientName() { return patientName; }
        public String getLocationName() { return locationName; }
        public String getSeverity() { return severity; }
        public String getAmbulanceId() { return ambulanceId; }
        public String getHospitalName() { return hospitalName; }
        public String getTimestamp() { return timestamp; }
        public String getStatus() { return status; }
    }

    /**
     * Records a completed emergency mission into the trips table.
     */
    public boolean insertTrip(String requestId, String patientName, String ambulanceId,
                              String hospitalId, double durationMins) {
        String sql = "INSERT INTO trips (request_id, patient_name, ambulance_id, hospital_id, end_time, duration_mins, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, 'COMPLETED')";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, requestId);
            ps.setString(2, patientName);
            ps.setString(3, ambulanceId);
            ps.setString(4, hospitalId);
            ps.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            ps.setDouble(6, durationMins);

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[TripDao] Error logging completed trip: " + e.getMessage());
            return false;
        }
    }

    /**
     * Reads all historical trip entries from the database, joined with
     * emergency and hospital tables for full human-readable audit history.
     */
    public List<TripRecord> getAllTrips() {
        List<TripRecord> list = new ArrayList<>();
        String sql = "SELECT t.id, t.request_id, t.patient_name, " +
                     "       COALESCE(l.name, 'Nalam Central') AS location_name, " +
                     "       COALESCE(e.severity, 'RESOLVED') AS severity, " +
                     "       t.ambulance_id, " +
                     "       COALESCE(h.name, t.hospital_id) AS hospital_name, " +
                     "       COALESCE(t.end_time, t.start_time) AS trip_time, " +
                     "       t.status " +
                     "FROM trips t " +
                     "LEFT JOIN emergencies e ON t.request_id = e.id " +
                     "LEFT JOIN locations l ON e.location_id = l.id " +
                     "LEFT JOIN hospitals h ON t.hospital_id = h.id " +
                     "ORDER BY t.id DESC";

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String reqId = rs.getString("request_id");
                String patName = rs.getString("patient_name");
                String locName = rs.getString("location_name");
                String severity = rs.getString("severity");
                String ambId = rs.getString("ambulance_id");
                String hospName = rs.getString("hospital_name");
                Timestamp ts = rs.getTimestamp("trip_time");
                String formattedTime = ts != null ? sdf.format(ts) : "12:00:00";
                String status = rs.getString("status");

                list.add(new TripRecord(reqId, patName, locName, severity, ambId, hospName, formattedTime, status));
            }
        } catch (SQLException e) {
            System.err.println("[TripDao] Error reading trip history: " + e.getMessage());
        }
        return list;
    }

    /**
     * Calculates the true average response/trip time from real completed trips.
     * Returns 0.0 if no completed trips exist yet.
     */
    public double getAverageResponseTime() {
        String sql = "SELECT AVG(duration_mins) AS avg_time FROM trips WHERE duration_mins > 0";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                double avg = rs.getDouble("avg_time");
                return rs.wasNull() ? 0.0 : Math.round(avg * 10.0) / 10.0;
            }
        } catch (SQLException e) {
            System.err.println("[TripDao] Error calculating average response time: " + e.getMessage());
        }
        return 0.0;
    }

    /**
     * Calculates the real on-time delivery rate from recorded trips.
     * Target window: <= 20 minutes from intake to hospital arrival.
     * Returns 0.0 if no trips recorded yet.
     */
    public double getOnTimeRate() {
        String sql = "SELECT COUNT(*) AS total, SUM(CASE WHEN duration_mins <= 20.0 THEN 1 ELSE 0 END) AS on_time FROM trips WHERE duration_mins > 0";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                int total = rs.getInt("total");
                int onTime = rs.getInt("on_time");
                if (total > 0) {
                    return Math.round(((double) onTime / total) * 1000.0) / 10.0;
                }
            }
        } catch (SQLException e) {
            System.err.println("[TripDao] Error calculating on-time rate: " + e.getMessage());
        }
        return 0.0;
    }

    /**
     * Clears all recorded trips (used during system reset).
     */
    public boolean clearAllTrips() {
        String sql = "DELETE FROM trips";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            return ps.executeUpdate() >= 0;
        } catch (SQLException e) {
            System.err.println("[TripDao] Error clearing trips: " + e.getMessage());
            return false;
        }
    }
}
