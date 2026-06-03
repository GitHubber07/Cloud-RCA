package com.rcacopilot.evaluator;

import com.rcacopilot.db.DatabaseManager;
import com.rcacopilot.llm.LlmClient;
import com.rcacopilot.model.Incident;
import com.rcacopilot.model.TelemetryLog;
import com.rcacopilot.similarity.FastTextEmbedder;
import com.rcacopilot.similarity.TemporalSimilaritySearch;
import com.rcacopilot.workflow.*;

import java.sql.SQLException;
import java.util.*;

public class Evaluator {
    private final Map<String, IncidentHandler> handlers;
    private final Map<String, String> summaryCache = new HashMap<>();

    public Evaluator() {
        this.handlers = new HashMap<>();
        setupWorkflowHandlers();
    }

    private void setupWorkflowHandlers() {
        // Define programmatic workflows for the alerts:
        
        // 1. ConnectionTimeoutAlert Handler
        IncidentHandler connHandler = new IncidentHandler("ConnectionTimeoutAlert", "QUERY_SOCKETS");
        connHandler.addAction(new QueryAction("QUERY_SOCKETS", "socket_metrics", "QUERY_PROBE"));
        connHandler.addAction(new QueryAction("QUERY_PROBE", "probe", "MITIGATE_CONN"));
        connHandler.addAction(new MitigationAction("MITIGATE_CONN", "Restart Transport proxy service to release ports."));
        handlers.put(connHandler.getAlertType(), connHandler);

        // 2. AuthenticationFailureAlert Handler
        IncidentHandler authHandler = new IncidentHandler("AuthenticationFailureAlert", "QUERY_EXCEPTIONS");
        authHandler.addAction(new QueryAction("QUERY_EXCEPTIONS", "exceptions", "QUERY_PROBE"));
        authHandler.addAction(new QueryAction("QUERY_PROBE", "probe", "MITIGATE_AUTH"));
        authHandler.addAction(new MitigationAction("MITIGATE_AUTH", "Re-provision authentication credentials and refresh certificates."));
        handlers.put(authHandler.getAlertType(), authHandler);

        // 3. QueueBacklogAlert Handler
        IncidentHandler queueHandler = new IncidentHandler("QueueBacklogAlert", "QUERY_EXCEPTIONS");
        queueHandler.addAction(new QueryAction("QUERY_EXCEPTIONS", "exceptions", "QUERY_THREADS"));
        queueHandler.addAction(new QueryAction("QUERY_THREADS", "thread_stacks", "QUERY_CONFIG"));
        queueHandler.addAction(new QueryAction("QUERY_CONFIG", "config", "MITIGATE_QUEUE"));
        queueHandler.addAction(new MitigationAction("MITIGATE_QUEUE", "Run delivery queue clearance and identify blocking database logs."));
        handlers.put(queueHandler.getAlertType(), queueHandler);

        // 4. DiskSpaceAlert Handler
        IncidentHandler diskHandler = new IncidentHandler("DiskSpaceAlert", "QUERY_EXCEPTIONS");
        diskHandler.addAction(new QueryAction("QUERY_EXCEPTIONS", "exceptions", "QUERY_PROBE"));
        diskHandler.addAction(new QueryAction("QUERY_PROBE", "probe", "MITIGATE_DISK"));
        diskHandler.addAction(new MitigationAction("MITIGATE_DISK", "Execute log rotation scripts and purge temporary dump files."));
        handlers.put(diskHandler.getAlertType(), diskHandler);
    }

    /**
     * Runs the evaluation pipeline over test incidents and prints performance metrics.
     */
    public void evaluate(
            List<Incident> testIncidents,
            List<Incident> historicalIncidents,
            DatabaseManager db,
            FastTextEmbedder embedder,
            LlmClient llm) throws Exception {

        System.out.println("\n=======================================================");
        System.out.println("            RCACopilot Simulation Evaluation           ");
        System.out.println("=======================================================");
        System.out.println("Test Set Size: " + testIncidents.size());
        System.out.println("History Set Size: " + historicalIncidents.size());
        System.out.println("-------------------------------------------------------\n");

        int correctPredictions = 0;
        int totalTestCount = testIncidents.size();

        // Data structures to compute Precision, Recall, F1 per category
        Map<String, Integer> truePositives = new HashMap<>();
        Map<String, Integer> falsePositives = new HashMap<>();
        Map<String, Integer> falseNegatives = new HashMap<>();

        for (Incident target : testIncidents) {
            System.out.println("Diagnosing incident: " + target.getId() + " [Alert: " + target.getAlertType() + "]");

            // 1. Diagnostic Data Collection
            IncidentHandler handler = handlers.get(target.getAlertType());
            if (handler == null) {
                System.out.println("  [Skip] No workflow handler registered for alert type: " + target.getAlertType());
                totalTestCount--;
                continue;
            }

            HandlerContext ctx = handler.run(target.getId(), "FOREST", "Forest-Wide", db);
            String rawDiagnosticData = ctx.getAccumulatedDiagnosticInfo();

            // 2. LLM Summarization
            String targetSummary = getCachedSummary(target.getId(), rawDiagnosticData, llm);

            // 3. Similarity Search & Retrieval
            List<Incident> neighbors = TemporalSimilaritySearch.findNearestNeighbors(
                    target, historicalIncidents, db, embedder, 5, 0.3
            );

            // 4. Construct Options & Prompt for Predictor
            List<String> options = new ArrayList<>();
            for (Incident neighbor : neighbors) {
                String neighborLogs = getAggregatedLogsText(neighbor.getId(), db);
                String neighborSummary = getCachedSummary(neighbor.getId(), neighborLogs, llm);
                options.add(neighborSummary + " ... category: " + neighbor.getTrueCategory());
            }

            // 5. Run prediction
            String predictionOutput = llm.predictRootCause(targetSummary, options);
            String predictedCategory = parsePredictedCategory(predictionOutput);

            // Update database record
            db.updatePrediction(target.getId(), predictedCategory, predictionOutput);

            System.out.println("  True Category     : " + target.getTrueCategory());
            System.out.println("  Predicted Category: " + predictedCategory);
            System.out.println("  Outcome           : " + (predictedCategory.equalsIgnoreCase(target.getTrueCategory()) ? "✔ SUCCESS" : "✘ FAILED"));
            System.out.println("  Reasoning Snippet : " + getSnippet(predictionOutput));
            System.out.println("-------------------------------------------------------");

            // Update confusion metrics
            String trueCat = target.getTrueCategory();
            String predCat = predictedCategory;

            if (trueCat.equalsIgnoreCase(predCat)) {
                correctPredictions++;
                truePositives.put(trueCat, truePositives.getOrDefault(trueCat, 0) + 1);
            } else {
                falsePositives.put(predCat, falsePositives.getOrDefault(predCat, 0) + 1);
                falseNegatives.put(trueCat, falseNegatives.getOrDefault(trueCat, 0) + 1);
            }
        }

        // Calculate and Print final Accuracy Metrics
        double microF1 = (double) correctPredictions / totalTestCount;
        
        System.out.println("\n=======================================================");
        System.out.println("                 FINAL EVALUATION SUMMARY              ");
        System.out.println("=======================================================");
        System.out.printf("Accuracy (Micro-F1): %.3f\n", microF1);

        // Compute Macro-F1
        double macroF1Sum = 0.0;
        int categoryCount = 0;
        Set<String> allCategories = new HashSet<>(Arrays.asList("HubPortExhaustion", "AuthCertIssue", "DeliveryHang", "FullDisk", "InvalidJournaling"));

        System.out.println("\nCategory Level Metrics:");
        System.out.printf("%-20s | %-10s | %-10s | %-10s\n", "Category", "Precision", "Recall", "F1-Score");
        System.out.println("---------------------|------------|------------|------------");

        for (String cat : allCategories) {
            int tp = truePositives.getOrDefault(cat, 0);
            int fp = falsePositives.getOrDefault(cat, 0);
            int fn = falseNegatives.getOrDefault(cat, 0);

            double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0.0;
            double recall = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0.0;
            double f1 = (precision + recall) > 0 ? 2 * (precision * recall) / (precision + recall) : 0.0;

            macroF1Sum += f1;
            categoryCount++;

            System.out.printf("%-20s | %-10.3f | %-10.3f | %-10.3f\n", cat, precision, recall, f1);
        }

        double macroF1 = macroF1Sum / categoryCount;
        System.out.println("-------------------------------------------------------");
        System.out.printf("Average Macro-F1   : %.3f\n", macroF1);
        System.out.println("=======================================================\n");
    }

    private String getCachedSummary(String id, String text, LlmClient llm) throws Exception {
        if (!summaryCache.containsKey(id)) {
            String summary = llm.summarize(text);
            summaryCache.put(id, summary.trim());
        }
        return summaryCache.get(id);
    }

    private String getAggregatedLogsText(String incidentId, DatabaseManager db) throws SQLException {
        List<TelemetryLog> logs = db.getTelemetryLogs(incidentId);
        StringBuilder sb = new StringBuilder();
        for (TelemetryLog log : logs) {
            sb.append(log.getMessage()).append(" ");
        }
        return sb.toString().trim();
    }

    private String parsePredictedCategory(String llmOutput) {
        String lower = llmOutput.toLowerCase();
        
        // Scan for standard categories
        if (lower.contains("predicted category: hubportexhaustion") || lower.contains("category: hubportexhaustion")) {
            return "HubPortExhaustion";
        }
        if (lower.contains("predicted category: authcertissue") || lower.contains("category: authcertissue")) {
            return "AuthCertIssue";
        }
        if (lower.contains("predicted category: deliveryhang") || lower.contains("category: deliveryhang")) {
            return "DeliveryHang";
        }
        if (lower.contains("predicted category: fulldisk") || lower.contains("category: fulldisk")) {
            return "FullDisk";
        }
        if (lower.contains("predicted category: invalidjournaling") || lower.contains("category: invalidjournaling")) {
            return "InvalidJournaling";
        }

        // Fallback checks
        if (lower.contains("hubportexhaustion")) return "HubPortExhaustion";
        if (lower.contains("authcertissue")) return "AuthCertIssue";
        if (lower.contains("deliveryhang")) return "DeliveryHang";
        if (lower.contains("fulldisk")) return "FullDisk";
        if (lower.contains("invalidjournaling")) return "InvalidJournaling";

        return "Unseen incident";
    }

    private String getSnippet(String output) {
        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.toLowerCase().contains("explanation") || line.toLowerCase().contains("reasoning")) {
                return line.length() > 100 ? line.substring(0, 100) + "..." : line;
            }
        }
        return output.length() > 100 ? output.substring(0, 100) + "..." : output;
    }
}
