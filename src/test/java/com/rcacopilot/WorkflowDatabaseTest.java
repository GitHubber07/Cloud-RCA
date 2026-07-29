package com.rcacopilot;

import com.rcacopilot.db.DatabaseManager;
import com.rcacopilot.evaluator.Evaluator;
import com.rcacopilot.workflow.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class WorkflowDatabaseTest {
    private DatabaseManager db;

    @BeforeEach
    public void setUp() throws Exception {
        db = new DatabaseManager();
        db.initializeSchema("schema.sql");
    }

    @AfterEach
    public void tearDown() throws Exception {
        db.clearDatabase();
    }

    @Test
    public void testSerializationAndDeserialization() {
        IncidentHandler handler = new IncidentHandler("TestAlert", "STEP1");
        
        QueryAction query = new QueryAction("STEP1", "exceptions", "STEP2");
        query.addKeywordRoute("timeout", "STEP3");
        
        ScopeSwitchAction scope = new ScopeSwitchAction("STEP2", "MACHINE", "Machine-abc", "STEP3");
        MitigationAction mitigation = new MitigationAction("STEP3", "Restart server.");
        
        handler.addAction(query);
        handler.addAction(scope);
        handler.addAction(mitigation);

        String json = Evaluator.serializeActions(handler);
        assertNotNull(json);
        assertTrue(json.contains("exceptions"));
        assertTrue(json.contains("timeout"));
        assertTrue(json.contains("Machine-abc"));
        assertTrue(json.contains("Restart server."));

        IncidentHandler deserialized = Evaluator.deserializeHandler("TestAlert", "STEP1", json);
        assertEquals("TestAlert", deserialized.getAlertType());
        assertEquals("STEP1", deserialized.getStartActionId());
        
        Map<String, Action> actions = deserialized.getActions();
        assertEquals(3, actions.size());
        
        Action a1 = actions.get("STEP1");
        assertTrue(a1 instanceof QueryAction);
        assertEquals("QUERY", a1.getType());
        
        Action a2 = actions.get("STEP2");
        assertTrue(a2 instanceof ScopeSwitchAction);
        assertEquals("SCOPE_SWITCH", a2.getType());
        
        Action a3 = actions.get("STEP3");
        assertTrue(a3 instanceof MitigationAction);
        assertEquals("MITIGATION", a3.getType());
    }

    @Test
    public void testDatabaseSaveAndRetrieve() throws SQLException {
        IncidentHandler handler = new IncidentHandler("ConnectionTimeoutAlert", "QUERY_SOCKETS");
        handler.addAction(new QueryAction("QUERY_SOCKETS", "socket_metrics", "MITIGATE_CONN"));
        handler.addAction(new MitigationAction("MITIGATE_CONN", "Restart proxy."));

        String json = Evaluator.serializeActions(handler);
        db.saveWorkflowHandler(handler.getAlertType(), handler.getStartActionId(), json);

        DatabaseManager.WorkflowData retrieved = db.getWorkflowHandler("ConnectionTimeoutAlert");
        assertNotNull(retrieved);
        assertEquals("ConnectionTimeoutAlert", retrieved.alertType);
        assertEquals("QUERY_SOCKETS", retrieved.startActionId);

        IncidentHandler retrievedHandler = Evaluator.deserializeHandler(retrieved.alertType, retrieved.startActionId, retrieved.actionsJson);
        assertNotNull(retrievedHandler);
        assertEquals(2, retrievedHandler.getActions().size());
        assertTrue(retrievedHandler.getActions().containsKey("QUERY_SOCKETS"));
        assertTrue(retrievedHandler.getActions().containsKey("MITIGATE_CONN"));
    }
}
