package com.rcacopilot.generator;

import com.rcacopilot.db.DatabaseManager;
import com.rcacopilot.model.Incident;
import com.rcacopilot.model.TelemetryLog;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;

public class MockDataGenerator {
    private static final String[] CATEGORIES = {
            "HubPortExhaustion", "AuthCertIssue", "DeliveryHang", "FullDisk", "InvalidJournaling"
    };

    private static final String[] ALERT_TYPES = {
            "ConnectionTimeoutAlert", "AuthenticationFailureAlert", "QueueBacklogAlert", "DiskSpaceAlert", "QueueBacklogAlert"
    };

    private final Random random = new Random(42); // Fixed seed for reproducibility

    /**
     * Generates a vocabulary of word vectors representing semantic clusters and writes to a FastText .vec file.
     */
    public void generateFastTextVecFile(String filePath) throws IOException {
        // We will generate 100-dimensional vectors for a vocabulary of ~50 key terms.
        // We define cluster coordinates for the 5 incident categories.
        int dimensions = 100;
        
        String[][] clusters = {
                // HubPortExhaustion: network, sockets, ports, connection
                {"connection", "socket", "port", "hub", "winsock", "udp", "11001", "exhausted", "network"},
                // AuthCertIssue: authentication, token, certificate, security, login
                {"auth", "token", "certificate", "cert", "expired", "invalid", "security", "credentials", "authentication"},
                // DeliveryHang: delivery, mailbox, queue, thread, blocked, hang
                {"delivery", "mailbox", "queue", "thread", "blocked", "hang", "stuck", "offline", "mailboxofflineexception"},
                // FullDisk: disk, storage, space, full, write, io, crash
                {"disk", "storage", "space", "full", "write", "io", "crash", "cleanup", "filesystem"},
                // InvalidJournaling: config, settings, journaling, tenant, tenantsettingsnotfoundexception
                {"config", "settings", "journaling", "tenant", "tenantsettingsnotfoundexception", "invalidconfig", "property", "missed"}
        };

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            int totalWords = 0;
            for (String[] cluster : clusters) totalWords += cluster.length;
            // Add a few neutral words
            String[] neutralWords = {"the", "and", "failed", "error", "log", "incident", "server", "process", "exception", "occurred"};
            totalWords += neutralWords.length;

            writer.write(totalWords + " " + dimensions + "\n");

            // Write cluster word vectors
            for (int clusterIdx = 0; clusterIdx < clusters.length; clusterIdx++) {
                double[] baseVector = new double[dimensions];
                // Assign a strong coordinate value to this cluster index
                baseVector[clusterIdx] = 10.0; 

                for (String word : clusters[clusterIdx]) {
                    writer.write(word);
                    for (int d = 0; d < dimensions; d++) {
                        // Add some small random noise around the cluster center
                        double val = baseVector[d] + (random.nextDouble() - 0.5) * 2.0;
                        writer.write(String.format(" %.4f", val));
                    }
                    writer.write("\n");
                }
            }

            // Write neutral word vectors (centered at origin)
            for (String word : neutralWords) {
                writer.write(word);
                for (int d = 0; d < dimensions; d++) {
                    double val = (random.nextDouble() - 0.5) * 2.0;
                    writer.write(String.format(" %.4f", val));
                }
                writer.write("\n");
            }
        }
    }

    /**
     * Generates history and test incidents and telemetry logs, writing them to Supabase.
     */
    public void generateAndInsertTelemetry(DatabaseManager db, int numHistory, int numTest) throws SQLException {
        Instant now = Instant.now();

        // 1. Generate History incidents (distributed over the past year)
        for (int i = 0; i < numHistory; i++) {
            int catIdx = random.nextInt(CATEGORIES.length);
            String category = CATEGORIES[catIdx];
            String alertType = ALERT_TYPES[catIdx];
            
            // Distributed back in time (e.g. 5 to 365 days ago)
            int daysAgo = 5 + random.nextInt(360);
            Instant timestamp = now.minus(daysAgo, ChronoUnit.DAYS);
            String incidentId = "INC-HIST-" + i;
            String title = String.format("High failure rate detected for %s in forest", alertType);

            Incident incident = new Incident(incidentId, timestamp, alertType, title, category);
            db.insertIncident(incident);

            generateLogsForIncident(db, incidentId, catIdx, timestamp);
        }

        // 2. Generate Test incidents (very recent: past 4 days)
        for (int i = 0; i < numTest; i++) {
            int catIdx = random.nextInt(CATEGORIES.length);
            String category = CATEGORIES[catIdx];
            String alertType = ALERT_TYPES[catIdx];

            int daysAgo = random.nextInt(4);
            Instant timestamp = now.minus(daysAgo, ChronoUnit.DAYS);
            String incidentId = "INC-TEST-" + i;
            String title = String.format("ACTIVE INCIDENT: %s trigger active", alertType);

            Incident incident = new Incident(incidentId, timestamp, alertType, title, category);
            db.insertIncident(incident);

            generateLogsForIncident(db, incidentId, catIdx, timestamp);
        }
    }

    private void generateLogsForIncident(DatabaseManager db, String incidentId, int catIdx, Instant baseTime) throws SQLException {
        // Insert standard multi-source diagnostic logs for this category
        String category = CATEGORIES[catIdx];

        switch (category) {
            case "HubPortExhaustion":
                db.insertTelemetryLog(new TelemetryLog(incidentId, "probe", "ERROR",
                        "DatacenterHubOutboundProxyProbe failed due to WinSock error 11001: No such host is known.", baseTime));
                db.insertTelemetryLog(new TelemetryLog(incidentId, "socket_metrics", "WARN",
                        "Total UDP socket count is 15276. Transport.exe is using 14923 connections, exceeding warning threshold.", baseTime.plusSeconds(30)));
                break;

            case "AuthCertIssue":
                db.insertTelemetryLog(new TelemetryLog(incidentId, "exceptions", "ERROR",
                        "AuthenticationException: Token generation failed. The security certificate is invalid.", baseTime));
                db.insertTelemetryLog(new TelemetryLog(incidentId, "probe", "ERROR",
                        "OAuth exchange failed with expired certificate domain.", baseTime.plusSeconds(30)));
                break;

            case "DeliveryHang":
                db.insertTelemetryLog(new TelemetryLog(incidentId, "exceptions", "ERROR",
                        "MailboxOfflineException: Target mailbox database is offline. Thread blocked in delivery service.", baseTime));
                db.insertTelemetryLog(new TelemetryLog(incidentId, "thread_stacks", "WARN",
                        "Thread ID 45: BLOCKED on MailboxDeliveryManager. Delivery queues are hang.", baseTime.plusSeconds(30)));
                break;

            case "FullDisk":
                db.insertTelemetryLog(new TelemetryLog(incidentId, "exceptions", "ERROR",
                        "IOException: No space left on device. Write operation failed on /dev/sda1.", baseTime));
                db.insertTelemetryLog(new TelemetryLog(incidentId, "probe", "ERROR",
                        "Host crashed during logs dump due to full storage space.", baseTime.plusSeconds(30)));
                break;

            case "InvalidJournaling":
                db.insertTelemetryLog(new TelemetryLog(incidentId, "exceptions", "ERROR",
                        "TenantSettingsNotFoundException: Configuration settings journaling property has invalid format.", baseTime));
                db.insertTelemetryLog(new TelemetryLog(incidentId, "config", "WARN",
                        "Tenant journaling config update failed. Transport defaults loaded instead.", baseTime.plusSeconds(30)));
                break;
        }
    }
}
