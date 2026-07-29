package com.rcacopilot.workflow;

import com.rcacopilot.db.DatabaseManager;

import java.util.HashMap;
import java.util.Map;

public class IncidentHandler {
    private final String alertType;
    private final String startActionId;
    private final Map<String, Action> actions;

    public IncidentHandler(String alertType, String startActionId) {
        this.alertType = alertType;
        this.startActionId = startActionId;
        this.actions = new HashMap<>();
    }

    public String getAlertType() { return alertType; }
    public String getStartActionId() { return startActionId; }
    public Map<String, Action> getActions() { return actions; }

    public void addAction(Action action) {
        actions.put(action.getId(), action);
    }

    /**
     * Executes the diagnostic workflow engine.
     */
    public HandlerContext run(String incidentId, String initialScope, String initialResource, DatabaseManager db) throws Exception {
        HandlerContext context = new HandlerContext(incidentId, initialScope, initialResource);
        
        context.appendDiagnosticInfo(String.format("Starting handler for alert: %s (Incident: %s)", alertType, incidentId));
        context.appendDiagnosticInfo(String.format("Initial Scope: %s (Target: %s)", initialScope, initialResource));

        String currentActionId = startActionId;
        int stepCount = 0;
        final int MAX_STEPS = 50; // Prevent infinite loops in cyclic graphs

        while (currentActionId != null) {
            stepCount++;
            if (stepCount > MAX_STEPS) {
                context.appendDiagnosticInfo("[ERROR] Diagnostic workflow exceeded maximum step limit. Potential infinite loop detected.");
                break;
            }

            Action action = actions.get(currentActionId);
            if (action == null) {
                context.appendDiagnosticInfo(String.format("[ERROR] Workflow step failed. Action with ID '%s' not found.", currentActionId));
                break;
            }

            ActionResult result = action.execute(context, db);
            currentActionId = result.getNextActionId();
        }

        context.appendDiagnosticInfo("--- Handler execution completed. ---");
        return context;
    }
}
