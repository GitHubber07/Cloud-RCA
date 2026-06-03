package com.rcacopilot.workflow;

import com.rcacopilot.db.DatabaseManager;

public class MitigationAction extends Action {
    private final String mitigationRecommendation;

    public MitigationAction(String id, String mitigationRecommendation) {
        super(id, "MITIGATION");
        this.mitigationRecommendation = mitigationRecommendation;
    }

    @Override
    public ActionResult execute(HandlerContext context, DatabaseManager db) throws Exception {
        context.setMitigationRecommendation(mitigationRecommendation);
        context.appendDiagnosticInfo(String.format("Recommended Mitigation Action: %s", mitigationRecommendation));
        // Return null to indicate the workflow has reached a terminal leaf node
        return new ActionResult(null);
    }
}
