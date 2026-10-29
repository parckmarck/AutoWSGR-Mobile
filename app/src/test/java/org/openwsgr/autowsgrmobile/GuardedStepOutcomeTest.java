package org.openwsgr.autowsgrmobile;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GuardedStepOutcomeTest {
    @Test public void matchedOutcomeCompletesWithoutStatusPhrase() {
        GuardedCombatStepper.Update update = GuardedCombatStepper.Update.terminal(
                GuardedCombatStepper.StepOutcome.MATCHED,
                "步骤已完成",
                "typed outcome controls completion"
        );

        assertTrue(GuardedCombatRunner.stepCompleted(update));
    }

    @Test public void blockedOutcomeDoesNotCompleteDespiteMatchedStatusPhrase() {
        GuardedCombatStepper.Update update = GuardedCombatStepper.Update.terminal(
                GuardedCombatStepper.StepOutcome.BLOCKED,
                "Guarded stepper: wait matched but guard refused",
                "typed outcome must win over localized text"
        );

        assertFalse(GuardedCombatRunner.stepCompleted(update));
    }
}
