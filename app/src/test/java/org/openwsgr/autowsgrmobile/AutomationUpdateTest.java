package org.openwsgr.autowsgrmobile;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class AutomationUpdateTest {
    @Test public void progressIsRunningAndNotTerminal() {
        AutomationUpdate update = AutomationUpdate.progress(
                "normal", "run-1", "正在出征", "detail", "run.log", ""
        );

        assertEquals(AutomationOutcome.RUNNING, update.outcome);
        assertTrue(update.running());
        assertFalse(update.outcome.terminal);
        assertThrows(IllegalArgumentException.class, () -> AutomationUpdate.terminal(
                "normal", "run-1", AutomationOutcome.RUNNING, "bad", "", "", ""
        ));
    }

    @Test public void everyNonRunningOutcomeIsTerminal() {
        for (AutomationOutcome outcome : AutomationOutcome.values()) {
            if (outcome == AutomationOutcome.RUNNING) {
                continue;
            }
            AutomationUpdate update = AutomationUpdate.terminal(
                    "flow", "run", outcome, outcome.name(), null, null, null
            );
            assertTrue(outcome.name(), outcome.terminal);
            assertFalse(outcome.name(), update.running());
        }
        assertThrows(IllegalArgumentException.class, () -> AutomationUpdate.terminal(
                "flow", "run", null, "bad", "", "", ""
        ));
    }

    @Test public void localizedMessageDoesNotChangeOutcome() {
        AutomationUpdate chinese = AutomationUpdate.terminal(
                "campaign", "run-2", AutomationOutcome.NO_ACTION,
                "没有可执行的任务", "", "", ""
        );
        AutomationUpdate english = AutomationUpdate.terminal(
                "campaign", "run-2", AutomationOutcome.NO_ACTION,
                "Nothing to do", "", "", ""
        );

        assertEquals(chinese.outcome, english.outcome);
        assertEquals(AutomationOutcome.NO_ACTION, chinese.outcome);
    }
}
