package com.rcacopilot.workflow;

import com.rcacopilot.db.DatabaseManager;

public class ScopeSwitchAction extends Action {
    private final String newScope;
    private final String targetResource;
    private final String nextActionId;

    public ScopeSwitchAction(String id, String newScope, String targetResource, String nextActionId) {
        super(id, "SCOPE_SWITCH");
        this.newScope = newScope;
        this.targetResource = targetResource;
        this.nextActionId = nextActionId;
    }

    @Override
    public ActionResult execute(HandlerContext context, DatabaseManager db) throws Exception {
        context.setCurrentScope(newScope);
        if (targetResource != null) {
            context.setTargetResource(targetResource);
        }
        context.appendDiagnosticInfo(String.format("Scope switched to %s (Target: %s)", newScope, context.getTargetResource()));
        return new ActionResult(nextActionId);
    }
}
