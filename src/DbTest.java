import db.Database;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Diagnostic test utility to verify database connectivity, driver registration,
 * schema initialization, and table record counts.
 *
 * Run with:
 *   java -cp "bin;lib/*" DbTest
 */
public class DbTest {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("  NALAM NAGAR DATABASE CONNECTION TEST UTILITY   ");
        System.out.println("=================================================");

        // 1. Initialize schema and seed data if needed
        System.out.println("\n[1] Initializing Database...");
        Database.initDatabase();

        if (args.length > 0 && args[0].equalsIgnoreCase("--reset")) {
            System.out.println("\n[RESET] Clearing test emergencies, trips, and resetting fleet & hospital beds...");
            new dao.EmergencyDao().clearAllEmergencies();
            new dao.TripDao().clearAllTrips();
            new dao.AmbulanceDao().resetFleet();
            new dao.HospitalDao().resetAllBeds();
            System.out.println("[RESET] Database clean reset completed successfully!");
        }

        // 2. Test Connection
        System.out.println("\n[2] Testing Active Connection...");
        try (Connection conn = Database.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            System.out.println(" -> Connected to Database Successfully!");
            System.out.println(" -> Product Name    : " + meta.getDatabaseProductName());
            System.out.println(" -> Product Version : " + meta.getDatabaseProductVersion());
            System.out.println(" -> Driver Name     : " + meta.getDriverName());
            System.out.println(" -> Driver Version  : " + meta.getDriverVersion());
            System.out.println(" -> Connection URL  : " + Database.getUrl());

            // 3. Print Row Counts for all core tables
            System.out.println("\n[3] Verifying Schema Tables & Row Counts:");
            String[] tables = {
                "locations",
                "ambulances",
                "hospitals",
                "hospital_specialties",
                "road_edges",
                "emergencies",
                "trips"
            };

            for (String table : tables) {
                int count = getRowCount(conn, table);
                if (count >= 0) {
                    System.out.printf("    %-22s : %3d rows [OK]%n", table, count);
                } else {
                    System.out.printf("    %-22s : [TABLE NOT FOUND OR ERROR]%n", table);
                }
            }

            System.out.println("\n=================================================");
            System.out.println(" [SUCCESS] Database configuration is fully operational!");
            System.out.println("=================================================");

        } catch (SQLException e) {
            System.err.println("\n[FAIL] Connection failed: " + e.getMessage());
            System.err.println("ErrorCode: " + e.getErrorCode() + " | SQLState: " + e.getSQLState());
            e.printStackTrace();
        }
    }

    private static int getRowCount(Connection conn, String tableName) {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            // Table might not exist yet
            return -1;
        }
        return -1;
    }
}
