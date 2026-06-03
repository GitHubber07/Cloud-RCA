package com.rcacopilot;

import com.rcacopilot.workflow.HandlerContext;
import com.rcacopilot.workflow.IncidentHandler;
import com.rcacopilot.workflow.MitigationAction;
import com.rcacopilot.workflow.ScopeSwitchAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class IncidentHandlerTest {

    @Test
    public void testWorkflowTraversal() throws Exception {
        IncidentHandler handler = new IncidentHandler("MockAlert", "STEP1");

        // Action 1: Switch scope and route to STEP2
        handler.addAction(new ScopeSwitchAction("STEP1", "MACHINE", "Machine-123", "STEP2"));
        // Action 2: Recommend mitigation and terminate
        handler.addAction(new MitigationAction("STEP2", "Restart virtual machine."));

        // Run the handler (passing null for DatabaseManager since these actions do not query DB)
        HandlerContext context = handler.run("INC-001", "FOREST", "Forest-Wide", null);

        assertEquals("MACHINE", context.getCurrentScope());
        assertEquals("Machine-123", context.getTargetResource());
        assertEquals("Restart virtual machine.", context.getMitigationRecommendation());

        String logs = context.getAccumulatedDiagnosticInfo();
        assertTrue(logs.contains("Scope switched to MACHINE"));
        assertTrue(logs.contains("Recommended Mitigation Action: Restart virtual machine."));
    }
}
