package com.rcacopilot.workflow;

import com.rcacopilot.db.DatabaseManager;
import com.rcacopilot.model.TelemetryLog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryAction extends Action {
    private final String querySource;
    private final String defaultNextActionId;
    private final Map<String, String> keywordRoutes; // keyword -> nextActionId

    public QueryAction(String id, String querySource, String defaultNextActionId) {
        super(id, "QUERY");
        this.querySource = querySource;
        this.defaultNextActionId = defaultNextActionId;
        this.keywordRoutes = new HashMap<>();
    }

    public void addKeywordRoute(String keyword, String nextActionId) {
        keywordRoutes.put(keyword.toLowerCase(), nextActionId);
    }

    @Override
    public ActionResult execute(HandlerContext context, DatabaseManager db) throws Exception {
        List<TelemetryLog> allLogs = db.getTelemetryLogs(context.getIncidentId());
        
        StringBuilder telemetryBuilder = new StringBuilder();
        telemetryBuilder.append(String.format("--- Telemetry Query: %s ---", querySource));

        boolean foundLogs = false;
        String routedActionId = null;

        for (TelemetryLog log : allLogs) {
            // Filter logs by source type and verify it applies to the current scope/resource if relevant
            if (log.getSource().equalsIgnoreCase(querySource)) {
                foundLogs = true;
                telemetryBuilder.append("\n").append(log.getMessage());

                // Evaluate routing rules based on message contents
                String messageLower = log.getMessage().toLowerCase();
                for (Map.Entry<String, String> entry : keywordRoutes.entrySet()) {
                    if (messageLower.contains(entry.getKey())) {
                        routedActionId = entry.getValue();
                        break;
                    }
                }
            }
        }

        if (!foundLogs) {
            telemetryBuilder.append("\n*No matching telemetry data found.*");
        }

        context.appendDiagnosticInfo(telemetryBuilder.toString());

        // Determine final routing path
        String nextActionId = (routedActionId != null) ? routedActionId : defaultNextActionId;
        return new ActionResult(nextActionId);
    }
}
