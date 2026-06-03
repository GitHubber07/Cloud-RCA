package com.rcacopilot.workflow;

import com.rcacopilot.db.DatabaseManager;

public abstract class Action {
    protected final String id;
    protected final String type;

    protected Action(String id, String type) {
        this.id = id;
        this.type = type;
    }

    public String getId() { return id; }
    public String getType() { return type; }

    /**
     * Executes the action within the context of an incident.
     *
     * @param context the context holding current diagnostic state.
     * @param db the database manager to fetch telemetry or save results.
     * @return the result indicating the next action to run.
     * @throws Exception if execution fails.
     */
    public abstract ActionResult execute(HandlerContext context, DatabaseManager db) throws Exception;
}
