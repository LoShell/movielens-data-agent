package com.jhz.movielens.web.task;

import com.jhz.movielens.agent.core.AgentEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedTaskTest {
    @Test
    void exposesCurrentWorkflowStepAndCompactsRepeatedProgressEvents() {
        ManagedTask task = new ManagedTask(
                "task-1", "clean data", "raw-v1", "clean-task-1", "quality-rules-v1");
        task.running();
        task.onAgentEvent(new AgentEvent(
                AgentEvent.Type.ACTION, "TOOL_SELECTED", "调用治理工具", "run_full_governance", 7));
        task.onAgentEvent(new AgentEvent(
                AgentEvent.Type.PROGRESS, "CLEANING", "Map 10%", "run_full_governance", 42));
        task.onAgentEvent(new AgentEvent(
                AgentEvent.Type.PROGRESS, "CLEANING", "Map 20%", "run_full_governance", 45));

        TaskSnapshot snapshot = task.snapshot();

        assertEquals(TaskStatus.RUNNING, snapshot.status());
        assertEquals("CLEANING", snapshot.stageCode());
        assertEquals(45, snapshot.progress());
        assertEquals("run_full_governance", snapshot.selectedTool());
        assertEquals(TaskStepSnapshot.Status.RUNNING, snapshot.steps().get(3).status());
        assertTrue(snapshot.steps().subList(0, 3).stream()
                .allMatch(step -> step.status() == TaskStepSnapshot.Status.DONE));
        assertEquals(3, snapshot.events().size());
        assertEquals("Map 20%", snapshot.events().get(2).message());
    }
}
