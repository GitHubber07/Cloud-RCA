package com.rcacopilot.evaluator;

import com.rcacopilot.db.DatabaseManager;
import com.rcacopilot.llm.LlmClient;
import com.rcacopilot.model.Incident;
import com.rcacopilot.model.TelemetryLog;
import com.rcacopilot.similarity.FastTextEmbedder;
import com.rcacopilot.similarity.TemporalSimilaritySearch;
import com.rcacopilot.workflow.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.sql.SQLException;
import java.util.*;

public class Evaluator {
    private final Map<String, String> summaryCache = new HashMap<>();
    
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Action.class, new ActionDeserializer())
            .create();

    public Evaluator() {
    }

    public static IncidentHandler deserializeHandler(String alertType, String startActionId, String actionsJson) {
        IncidentHandler handler = new IncidentHandler(alertType, startActionId);
        Action[] actions = GSON.fromJson(actionsJson, Action[].class);
        if (actions != null) {
            for (Action action : actions) {
                handler.addAction(action);
            }
        }
        return handler;
    }

    public static String serializeActions(IncidentHandler handler) {
        return GSON.toJson(handler.getActions().values());
    }

    public static IncidentHandler getWorkflowHandler(String alertType, DatabaseManager db) throws SQLException {
        DatabaseManager.WorkflowData data = db.getWorkflowHandler(alertType);
        if (data != null) {
            return deserializeHandler(data.alertType, data.startActionId, data.actionsJson);
        }
        return null;
    }

    public void seedDefaultWorkflows(DatabaseManager db) throws SQLException {
        if (db.getAllWorkflowHandlers().isEmpty()) {
            System.out.println("Seeding default workflow handlers into the database...");
            
            // 1. ConnectionTimeoutAlert
            IncidentHandler connHandler = new IncidentHandler("ConnectionTimeoutAlert", "QUERY_SOCKETS");
            connHandler.addAction(new QueryAction("QUERY_SOCKETS", "socket_metrics", "QUERY_PROBE"));
            connHandler.addAction(new QueryAction("QUERY_PROBE", "probe", "MITIGATE_CONN"));
            connHandler.addAction(new MitigationAction("MITIGATE_CONN", "Restart Transport proxy service to release ports."));
            db.saveWorkflowHandler(connHandler.getAlertType(), connHandler.getStartActionId(), serializeActions(connHandler));

            // 2. AuthenticationFailureAlert
            IncidentHandler authHandler = new IncidentHandler("AuthenticationFailureAlert", "QUERY_EXCEPTIONS");
            authHandler.addAction(new QueryAction("QUERY_EXCEPTIONS", "exceptions", "QUERY_PROBE"));
            authHandler.addAction(new QueryAction("QUERY_PROBE", "probe", "MITIGATE_AUTH"));
            authHandler.addAction(new MitigationAction("MITIGATE_AUTH", "Re-provision authentication credentials and refresh certificates."));
            db.saveWorkflowHandler(authHandler.getAlertType(), authHandler.getStartActionId(), serializeActions(authHandler));

            // 3. QueueBacklogAlert
            IncidentHandler queueHandler = new IncidentHandler("QueueBacklogAlert", "QUERY_EXCEPTIONS");
            queueHandler.addAction(new QueryAction("QUERY_EXCEPTIONS", "exceptions", "QUERY_THREADS"));
            queueHandler.addAction(new QueryAction("QUERY_THREADS", "thread_stacks", "QUERY_CONFIG"));
            queueHandler.addAction(new QueryAction("QUERY_CONFIG", "config", "MITIGATE_QUEUE"));
            queueHandler.addAction(new MitigationAction("MITIGATE_QUEUE", "Run delivery queue clearance and identify blocking database logs."));
            db.saveWorkflowHandler(queueHandler.getAlertType(), queueHandler.getStartActionId(), serializeActions(queueHandler));

            // 4. DiskSpaceAlert
            IncidentHandler diskHandler = new IncidentHandler("DiskSpaceAlert", "QUERY_EXCEPTIONS");
            diskHandler.addAction(new QueryAction("QUERY_EXCEPTIONS", "exceptions", "QUERY_PROBE"));
            diskHandler.addAction(new QueryAction("QUERY_PROBE", "probe", "MITIGATE_DISK"));
            diskHandler.addAction(new MitigationAction("MITIGATE_DISK", "Execute log rotation scripts and purge temporary dump files."));
            db.saveWorkflowHandler(diskHandler.getAlertType(), diskHandler.getStartActionId(), serializeActions(diskHandler));
            
            System.out.println("Default workflow handlers seeded successfully.");
        }
    }

    /**
     * Runs the evaluation pipeline over test incidents and prints performance metrics.
     */
    public static class EvaluationResult {
        public final double microF1;
        public final double macroF1;

        public EvaluationResult(double microF1, double macroF1) {
            this.microF1 = microF1;
            this.macroF1 = macroF1;
        }
    }

    public EvaluationResult evaluate(
            List<Incident> testIncidents,
            List<Incident> historicalIncidents,
            DatabaseManager db,
            FastTextEmbedder embedder,
            LlmClient llm) throws Exception {
        return evaluate(testIncidents, historicalIncidents, db, embedder, llm, 5, 0.3, false);
    }

    public EvaluationResult evaluate(
            List<Incident> testIncidents,
            List<Incident> historicalIncidents,
            DatabaseManager db,
            FastTextEmbedder embedder,
            LlmClient llm,
            int K,
            double alpha) throws Exception {
        return evaluate(testIncidents, historicalIncidents, db, embedder, llm, K, alpha, false);
    }

    public EvaluationResult evaluate(
            List<Incident> testIncidents,
            List<Incident> historicalIncidents,
            DatabaseManager db,
            FastTextEmbedder embedder,
            LlmClient llm,
            int K,
            double alpha,
            boolean silent) throws Exception {

        // Ensure default workflows are seeded in the database before running evaluation
        seedDefaultWorkflows(db);

        if (!silent) {
            System.out.println("\n=======================================================");
            System.out.println("            RCACopilot Simulation Evaluation           ");
            System.out.println("=======================================================");
            System.out.println("Test Set Size: " + testIncidents.size());
            System.out.println("History Set Size: " + historicalIncidents.size());
            System.out.println("Parameters: K=" + K + ", Alpha=" + alpha);
            System.out.println("-------------------------------------------------------\n");
        }

        int correctPredictions = 0;
        int totalTestCount = testIncidents.size();

        // Data structures to compute Precision, Recall, F1 per category
        Map<String, Integer> truePositives = new HashMap<>();
        Map<String, Integer> falsePositives = new HashMap<>();
        Map<String, Integer> falseNegatives = new HashMap<>();

        for (Incident target : testIncidents) {
            if (!silent) {
                System.out.println("Diagnosing incident: " + target.getId() + " [Alert: " + target.getAlertType() + "]");
            }

            // 1. Diagnostic Data Collection
            IncidentHandler handler = getWorkflowHandler(target.getAlertType(), db);
            if (handler == null) {
                if (!silent) {
                    System.out.println("  [Skip] No workflow handler registered in DB for alert type: " + target.getAlertType());
                }
                totalTestCount--;
                continue;
            }

            HandlerContext ctx = handler.run(target.getId(), "FOREST", "Forest-Wide", db);
            String rawDiagnosticData = ctx.getAccumulatedDiagnosticInfo();

            // 2. LLM Summarization
            String targetSummary = getCachedSummary(target.getId(), rawDiagnosticData, llm);

            // 3. Similarity Search & Retrieval
            List<Incident> neighbors = TemporalSimilaritySearch.findNearestNeighbors(
                    target, historicalIncidents, db, embedder, K, alpha
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

            if (!silent) {
                System.out.println("  True Category     : " + target.getTrueCategory());
                System.out.println("  Predicted Category: " + predictedCategory);
                System.out.println("  Outcome           : " + (predictedCategory.equalsIgnoreCase(target.getTrueCategory()) ? "✔ SUCCESS" : "✘ FAILED"));
                System.out.println("  Reasoning Snippet : " + getSnippet(predictionOutput));
                System.out.println("-------------------------------------------------------");
            }

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
        double microF1 = totalTestCount > 0 ? (double) correctPredictions / totalTestCount : 0.0;
        
        if (!silent) {
            System.out.println("\n=======================================================");
            System.out.println("                 FINAL EVALUATION SUMMARY              ");
            System.out.println("=======================================================");
            System.out.printf("Accuracy (Micro-F1): %.3f\n", microF1);
        }

        // Compute Macro-F1
        double macroF1Sum = 0.0;
        int categoryCount = 0;
        Set<String> allCategories = new HashSet<>(Arrays.asList("HubPortExhaustion", "AuthCertIssue", "DeliveryHang", "FullDisk", "InvalidJournaling"));

        if (!silent) {
            System.out.println("\nCategory Level Metrics:");
            System.out.printf("%-20s | %-10s | %-10s | %-10s\n", "Category", "Precision", "Recall", "F1-Score");
            System.out.println("---------------------|------------|------------|------------");
        }

        for (String cat : allCategories) {
            int tp = truePositives.getOrDefault(cat, 0);
            int fp = falsePositives.getOrDefault(cat, 0);
            int fn = falseNegatives.getOrDefault(cat, 0);

            double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0.0;
            double recall = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0.0;
            double f1 = (precision + recall) > 0 ? 2 * (precision * recall) / (precision + recall) : 0.0;

            macroF1Sum += f1;
            categoryCount++;

            if (!silent) {
                System.out.printf("%-20s | %-10.3f | %-10.3f | %-10.3f\n", cat, precision, recall, f1);
            }
        }

        double macroF1 = macroF1Sum / categoryCount;
        if (!silent) {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Average Macro-F1   : %.3f\n", macroF1);
            System.out.println("=======================================================\n");
        }

        return new EvaluationResult(microF1, macroF1);
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
