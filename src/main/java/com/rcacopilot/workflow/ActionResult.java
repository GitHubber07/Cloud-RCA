package com.rcacopilot.workflow;

public class ActionResult {
    private final String nextActionId;

    public ActionResult(String nextActionId) {
        this.nextActionId = nextActionId;
    }

    public String getNextActionId() {
        return nextActionId;
    }
}
