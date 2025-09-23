package Client1;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhaseSynchronizationTest {

    @Test
    void phase2StartsOnlyAfterThresholdMet() throws InterruptedException {
        int totalThreads = 10;
        CountDownLatch triggerLatch = SkClient1.createPhase2TriggerLatch(totalThreads);
        int requiredCompletions = SkClient1.calculatePhase2TriggerCount(totalThreads);

        for (int i = 0; i < requiredCompletions - 1; i++) {
            triggerLatch.countDown();
        }

        assertFalse(triggerLatch.await(100, TimeUnit.MILLISECONDS),
                "Phase 2 should not begin before the configured completion threshold is satisfied");

        triggerLatch.countDown();

        assertTrue(triggerLatch.await(100, TimeUnit.MILLISECONDS),
                "Phase 2 should begin once the completion threshold is satisfied");
    }
}
