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

    public static class WorkflowData {
        public String alertType;
        public String startActionId;
        public String actionsJson;
    }

    public DatabaseManager() {
        String envUrl = System.getenv("SUPABASE_DB_URL");
        String envUser = System.getenv("SUPABASE_DB_USER");
        String envPassword = System.getenv("SUPABASE_DB_PASSWORD");

        if (envUrl == null || envUser == null || envPassword == null) {
            System.out.println("[DatabaseManager] Database environment variables missing. Falling back to local H2 database.");
            this.dbUrl = "jdbc:h2:./h2_rca_db;MODE=PostgreSQL;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";
            this.dbUser = "sa";
            this.dbPassword = "";
            try {
                Class.forName("org.h2.Driver");
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("H2 JDBC Driver not found on the classpath! Please check if H2 is added to pom.xml.", e);
            }
        } else {
            this.dbUrl = envUrl;
            this.dbUser = envUser;
            this.dbPassword = envPassword;
            try {
                Class.forName("org.postgresql.Driver");
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("PostgreSQL JDBC Driver not found on the classpath!", e);
            }
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
        // Split SQL statements by semicolon
        String[] statements = schemaContent.split(";");

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                // Filter out comment lines from the statement
                StringBuilder cleanSql = new StringBuilder();
                String[] lines = sql.split("\n");
                for (String line : lines) {
                    String trimmedLine = line.trim();
                    if (!trimmedLine.startsWith("--")) {
                        cleanSql.append(line).append("\n");
                    }
                }
                String finalSql = cleanSql.toString().trim();
                if (!finalSql.isEmpty()) {
                    stmt.execute(finalSql);
                }
            }
        }
    }

    /**
     * Inserts an incident into the database.
     */
    public void insertIncident(Incident incident) throws SQLException {
        String selectSql = "SELECT 1 FROM incidents WHERE id = ?";
        String updateSql = "UPDATE incidents SET timestamp = ?, alert_type = ?, title = ?, true_category = ?, predicted_category = ?, explanation = ? WHERE id = ?";
        String insertSql = "INSERT INTO incidents (id, timestamp, alert_type, title, true_category, predicted_category, explanation) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection()) {
            boolean exists = false;
            try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
                pstmt.setString(1, incident.getId());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        exists = true;
                    }
                }
            }

            if (exists) {
                try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                    pstmt.setTimestamp(1, Timestamp.from(incident.getTimestamp()));
                    pstmt.setString(2, incident.getAlertType());
                    pstmt.setString(3, incident.getTitle());
                    pstmt.setString(4, incident.getTrueCategory());
                    pstmt.setString(5, incident.getPredictedCategory());
                    pstmt.setString(6, incident.getExplanation());
                    pstmt.setString(7, incident.getId());
                    pstmt.executeUpdate();
                }
            } else {
                try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
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
            try {
                stmt.executeUpdate("TRUNCATE TABLE telemetry_logs, incidents CASCADE");
            } catch (SQLException e) {
                // Fallback for H2 or engines that don't support multi-table cascade truncate
                stmt.executeUpdate("DELETE FROM telemetry_logs");
                stmt.executeUpdate("DELETE FROM incidents");
                stmt.executeUpdate("DELETE FROM workflow_handlers");
            }
        }
    }

    /**
     * Saves or updates a workflow handler.
     */
    public void saveWorkflowHandler(String alertType, String startActionId, String actionsJson) throws SQLException {
        String selectSql = "SELECT 1 FROM workflow_handlers WHERE alert_type = ?";
        String updateSql = "UPDATE workflow_handlers SET start_action_id = ?, actions = ? WHERE alert_type = ?";
        String insertSql = "INSERT INTO workflow_handlers (alert_type, start_action_id, actions) VALUES (?, ?, ?)";

        try (Connection conn = getConnection()) {
            boolean exists = false;
            try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
                pstmt.setString(1, alertType);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        exists = true;
                    }
                }
            }

            if (exists) {
                try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                    pstmt.setString(1, startActionId);
                    pstmt.setString(2, actionsJson);
                    pstmt.setString(3, alertType);
                    pstmt.executeUpdate();
                }
            } else {
                try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                    pstmt.setString(1, alertType);
                    pstmt.setString(2, startActionId);
                    pstmt.setString(3, actionsJson);
                    pstmt.executeUpdate();
                }
            }
        }
    }

    /**
     * Fetches a workflow handler from the database.
     */
    public WorkflowData getWorkflowHandler(String alertType) throws SQLException {
        String sql = "SELECT start_action_id, actions FROM workflow_handlers WHERE alert_type = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, alertType);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    WorkflowData data = new WorkflowData();
                    data.alertType = alertType;
                    data.startActionId = rs.getString("start_action_id");
                    data.actionsJson = rs.getString("actions");
                    return data;
                }
            }
        }
        return null;
    }

    /**
     * Fetches all workflow handlers from the database.
     */
    public List<WorkflowData> getAllWorkflowHandlers() throws SQLException {
        List<WorkflowData> list = new ArrayList<>();
        String sql = "SELECT alert_type, start_action_id, actions FROM workflow_handlers";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                WorkflowData data = new WorkflowData();
                data.alertType = rs.getString("alert_type");
                data.startActionId = rs.getString("start_action_id");
                data.actionsJson = rs.getString("actions");
                list.add(data);
            }
        }
        return list;
    }
}
