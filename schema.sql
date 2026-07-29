-- SQL Schema for RCACopilot database on Supabase

-- Drop tables if they exist to allow clean recreations
DROP TABLE IF EXISTS telemetry_logs CASCADE;
DROP TABLE IF EXISTS incidents CASCADE;
DROP TABLE IF EXISTS workflow_handlers CASCADE;

-- Table to store incident metadata and final predictions
CREATE TABLE incidents (
    id VARCHAR(100) PRIMARY KEY,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    alert_type VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    true_category VARCHAR(100) NOT NULL,
    predicted_category VARCHAR(100),
    explanation TEXT
);

-- Table to store multi-source diagnostic telemetry (logs, metrics, stack traces)
CREATE TABLE telemetry_logs (
    id SERIAL PRIMARY KEY,
    incident_id VARCHAR(100) NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    source VARCHAR(100) NOT NULL, -- e.g., 'probe', 'exceptions', 'socket_metrics', 'thread_stacks'
    log_level VARCHAR(20) NOT NULL, -- e.g., 'INFO', 'WARN', 'ERROR'
    message TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Table to store workflow handlers (JSON list of Action nodes)
CREATE TABLE workflow_handlers (
    alert_type VARCHAR(100) PRIMARY KEY,
    start_action_id VARCHAR(100) NOT NULL,
    actions TEXT NOT NULL
);

-- Indices for faster similarity search retrieval and lookups
CREATE INDEX idx_incidents_alert_type ON incidents(alert_type);
CREATE INDEX idx_incidents_timestamp ON incidents(timestamp DESC);
CREATE INDEX idx_telemetry_logs_incident_id ON telemetry_logs(incident_id);
