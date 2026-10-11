package db;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Database connection manager for Nalam Nagar Emergency Operations.
 * Reads credentials securely from db.properties or environment variables.
 * Automatically initializes tables and seed data if the database is fresh.
 */
public class Database {

    private static String url;
    private static String user;
    private static String password;
    private static String driver;

    static {
        loadConfig();
    }

    private static void loadConfig() {
        Properties props = new Properties();

        // 1. Try loading from db.properties file
        try (InputStream in = Files.exists(Paths.get("db.properties"))
                ? new FileInputStream("db.properties")
                : Database.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception e) {
            System.err.println("[Database] Warning: Could not read db.properties file: " + e.getMessage());
        }

        // 2. Read properties with Environment Variable overrides
        driver = System.getenv("DB_DRIVER");
        if (driver == null || driver.isEmpty()) {
            driver = props.getProperty("db.driver", "org.sqlite.JDBC");
        }

        url = System.getenv("DB_URL");
        if (url == null || url.isEmpty()) {
            url = props.getProperty("db.url", "jdbc:sqlite:nalam_nagar.db");
        }

        user = System.getenv("DB_USER");
        if (user == null) {
            user = props.getProperty("db.user", "");
        }

        password = System.getenv("DB_PASSWORD");
        if (password == null) {
            password = props.getProperty("db.password", "");
        }

        // 3. Register the JDBC Driver class
        try {
            Class.forName(driver);
            System.out.println("[Database] Loaded JDBC Driver: " + driver);
        } catch (ClassNotFoundException e) {
            System.err.println("[Database] ERROR: Driver class not found in classpath: " + driver);
            System.err.println("Ensure driver jar is inside lib/ and added to the classpath.");
        }
    }

    /**
     * Obtains a new database connection.
     * Always remember to use try-with-resources:
     * try (Connection conn = Database.getConnection()) { ... }
     */
    public static Connection getConnection() throws SQLException {
        if (user == null || user.isEmpty()) {
            return DriverManager.getConnection(url);
        }
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * Verifies if database tables exist, and runs schema.sql if empty.
     */
    public static void initDatabase() {
        try (Connection conn = getConnection()) {
            boolean tablesExist = false;

            // Check if locations table already exists
            try (Statement stmt = conn.createStatement()) {
                String checkSql = url.contains("sqlite")
                        ? "SELECT name FROM sqlite_master WHERE type='table' AND name='locations'"
                        : "SELECT table_name FROM information_schema.tables WHERE table_name = 'locations'";
                try (ResultSet rs = stmt.executeQuery(checkSql)) {
                    if (rs.next()) {
                        tablesExist = true;
                    }
                }
            }

            if (!tablesExist) {
                System.out.println("[Database] Initializing database tables and seed data from schema.sql...");
                executeSqlScript(conn, "schema.sql");
                System.out.println("[Database] Database initialized successfully.");
            } else {
                System.out.println("[Database] Existing tables detected. Connected to: " + url);
            }
        } catch (SQLException e) {
            System.err.println("[Database] Connection test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void executeSqlScript(Connection conn, String scriptPath) {
        if (!Files.exists(Paths.get(scriptPath))) {
            System.err.println("[Database] Could not find SQL script: " + scriptPath);
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(scriptPath));
             Statement stmt = conn.createStatement()) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("--") || line.isEmpty()) {
                    continue;
                }
                sb.append(line).append(" ");
                if (line.endsWith(";")) {
                    String sql = sb.toString().trim();
                    sql = sql.substring(0, sql.length() - 1); // remove semicolon
                    try {
                        stmt.execute(sql);
                    } catch (SQLException ex) {
                        // ignore drop errors if tables don't exist yet
                        if (!sql.toUpperCase().startsWith("DROP")) {
                            System.err.println("[Database] Warning executing statement: " + ex.getMessage());
                        }
                    }
                    sb.setLength(0);
                }
            }
        } catch (Exception e) {
            System.err.println("[Database] Error running " + scriptPath + ": " + e.getMessage());
        }
    }

    public static String getUrl() {
        return url;
    }
}
