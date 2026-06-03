package com.rcacopilot.db;

import com.rcacopilot.model.Incident;
import com.rcacopilot.model.TelemetryLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    public DatabaseManager() {
        this.dbUrl = System.getenv("SUPABASE_DB_URL");
        this.dbUser = System.getenv("SUPABASE_DB_USER");
        this.dbPassword = System.getenv("SUPABASE_DB_PASSWORD");

        // Validate environment configuration
        if (dbUrl == null || dbUser == null || dbPassword == null) {
            throw new IllegalStateException("Database configuration missing!\n" +
                    "Please set the following environment variables:\n" +
                    "  - SUPABASE_DB_URL (e.g. jdbc:postgresql://<host>:<port>/<db>)\n" +
                    "  - SUPABASE_DB_USER\n" +
                    "  - SUPABASE_DB_PASSWORD");
        }

        // Pre-load PostgreSQL Driver explicitly to guarantee availability
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL JDBC Driver not found on the classpath!", e);
        }
    }

    /**
     * Obtains a connection to the Supabase PostgreSQL database.
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    /**
     * Executes the SQL statements in the schema file to initialize the database structure.
     */
    public void initializeSchema(String schemaFilePath) throws SQLException, IOException {
        String schemaContent = new String(Files.readAllBytes(Paths.get(schemaFilePath)));
        // Split SQL statements by semicolon, excluding comments
        String[] statements = schemaContent.split(";");

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                String trimmedSql = sql.trim();
                // Skip empty blocks or commentary lines
                if (!trimmedSql.isEmpty() && !trimmedSql.startsWith("--")) {
                    stmt.execute(trimmedSql);
                }
            }
        }
    }

    /**
     * Inserts an incident into the database.
     */
    public void insertIncident(Incident incident) throws SQLException {
        String sql = "INSERT INTO incidents (id, timestamp, alert_type, title, true_category, predicted_category, explanation) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT (id) DO UPDATE SET " +
                     "timestamp = EXCLUDED.timestamp, alert_type = EXCLUDED.alert_type, title = EXCLUDED.title, " +
                     "true_category = EXCLUDED.true_category, predicted_category = EXCLUDED.predicted_category, " +
                     "explanation = EXCLUDED.explanation";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, incident.getId());
            pstmt.setTimestamp(2, Timestamp.from(incident.getTimestamp()));
            pstmt.setString(3, incident.getAlertType());
            pstmt.setString(4, incident.getTitle());
            pstmt.setString(5, incident.getTrueCategory());
            pstmt.setString(6, incident.getPredictedCategory());
            pstmt.setString(7, incident.getExplanation());
            pstmt.executeUpdate();
        }
    }

    /**
     * Inserts a telemetry log record.
     */
    public void insertTelemetryLog(TelemetryLog log) throws SQLException {
        String sql = "INSERT INTO telemetry_logs (incident_id, source, log_level, message, created_at) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, log.getIncidentId());
            pstmt.setString(2, log.getSource());
            pstmt.setString(3, log.getLogLevel());
            pstmt.setString(4, log.getMessage());
            pstmt.setTimestamp(5, Timestamp.from(log.getCreatedAt()));
            pstmt.executeUpdate();
        }
    }

    /**
     * Fetches all incidents in the database ordered by timestamp desc.
     */
    public List<Incident> getAllIncidents() throws SQLException {
        List<Incident> incidents = new ArrayList<>();
        String sql = "SELECT id, timestamp, alert_type, title, true_category, predicted_category, explanation FROM incidents ORDER BY timestamp DESC";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                Incident incident = new Incident();
                incident.setId(rs.getString("id"));
                incident.setTimestamp(rs.getTimestamp("timestamp").toInstant());
                incident.setAlertType(rs.getString("alert_type"));
                incident.setTitle(rs.getString("title"));
                incident.setTrueCategory(rs.getString("true_category"));
                incident.setPredictedCategory(rs.getString("predicted_category"));
                incident.setExplanation(rs.getString("explanation"));
                incidents.add(incident);
            }
        }
        return incidents;
    }

    /**
     * Fetches telemetry logs for a specific incident.
     */
    public List<TelemetryLog> getTelemetryLogs(String incidentId) throws SQLException {
        List<TelemetryLog> logs = new ArrayList<>();
        String sql = "SELECT id, incident_id, source, log_level, message, created_at FROM telemetry_logs WHERE incident_id = ? ORDER BY created_at ASC";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, incidentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    TelemetryLog log = new TelemetryLog();
                    log.setId(rs.getInt("id"));
                    log.setIncidentId(rs.getString("incident_id"));
                    log.setSource(rs.getString("source"));
                    log.setLogLevel(rs.getString("log_level"));
                    log.setMessage(rs.getString("message"));
                    log.setCreatedAt(rs.getTimestamp("created_at").toInstant());
                    logs.add(log);
                }
            }
        }
        return logs;
    }

    /**
     * Updates an incident with predicted category and LLM explanation.
     */
    public void updatePrediction(String incidentId, String predictedCategory, String explanation) throws SQLException {
        String sql = "UPDATE incidents SET predicted_category = ?, explanation = ? WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, predictedCategory);
            pstmt.setString(2, explanation);
            pstmt.setString(3, incidentId);
            pstmt.executeUpdate();
        }
    }

    /**
     * Clears all tables in the database.
     */
    public void clearDatabase() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("TRUNCATE TABLE telemetry_logs, incidents CASCADE");
        }
    }
}
