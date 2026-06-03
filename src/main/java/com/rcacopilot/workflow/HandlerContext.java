package com.rcacopilot.workflow;

import java.util.HashMap;
import java.util.Map;

public class HandlerContext {
    private final String incidentId;
    private String currentScope; // e.g. "FOREST", "MACHINE"
    private String targetResource; // e.g. "Forest-A", "Machine-102"
    private final StringBuilder accumulatedDiagnosticInfo;
    private String mitigationRecommendation;
    private final Map<String, String> variables;

    public HandlerContext(String incidentId, String initialScope, String initialResource) {
        this.incidentId = incidentId;
        this.currentScope = initialScope;
        this.targetResource = initialResource;
        this.accumulatedDiagnosticInfo = new StringBuilder();
        this.variables = new HashMap<>();
    }

    public String getIncidentId() { return incidentId; }

    public String getCurrentScope() { return currentScope; }
    public void setCurrentScope(String currentScope) { this.currentScope = currentScope; }

    public String getTargetResource() { return targetResource; }
    public void setTargetResource(String targetResource) { this.targetResource = targetResource; }

    public void appendDiagnosticInfo(String info) {
        if (accumulatedDiagnosticInfo.length() > 0) {
            accumulatedDiagnosticInfo.append("\n");
        }
        accumulatedDiagnosticInfo.append(info);
    }

    public String getAccumulatedDiagnosticInfo() {
        return accumulatedDiagnosticInfo.toString();
    }

    public String getMitigationRecommendation() { return mitigationRecommendation; }
    public void setMitigationRecommendation(String mitigationRecommendation) {
        this.mitigationRecommendation = mitigationRecommendation;
    }

    public void setVariable(String key, String value) { variables.put(key, value); }
    public String getVariable(String key) { return variables.get(key); }
}
