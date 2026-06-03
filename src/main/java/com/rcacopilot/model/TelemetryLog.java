package com.rcacopilot.model;

import java.time.Instant;

public class TelemetryLog {
    private int id;
    private String incidentId;
    private String source;
    private String logLevel;
    private String message;
    private Instant createdAt;

    public TelemetryLog() {}

    public TelemetryLog(String incidentId, String source, String logLevel, String message, Instant createdAt) {
        this.incidentId = incidentId;
        this.source = source;
        this.logLevel = logLevel;
        this.message = message;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getIncidentId() { return incidentId; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getLogLevel() { return logLevel; }
    public void setLogLevel(String logLevel) { this.logLevel = logLevel; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "TelemetryLog{" +
                "incidentId='" + incidentId + '\'' +
                ", source='" + source + '\'' +
                ", logLevel='" + logLevel + '\'' +
                ", messageSummary='" + (message.length() > 50 ? message.substring(0, 50) + "..." : message) + '\'' +
                '}';
    }
}
