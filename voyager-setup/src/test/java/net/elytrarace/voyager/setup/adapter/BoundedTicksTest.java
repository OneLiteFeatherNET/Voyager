package net.elytrarace.voyager.setup.adapter;

import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The test wait is bounded by a tick count, so a condition that never clears fails the test without reading a clock. */
@EnvTest
class BoundedTicksTest {

    @Test
    void aConditionThatNeverClearsFailsOnceTheTickBudgetIsSpent(Env env) {
        AtomicInteger checks = new AtomicInteger();

        assertThatThrownBy(() -> BoundedTicks.tickWhile(env, () -> {
            checks.incrementAndGet();
            return true;
        })).isInstanceOf(AssertionError.class)
                .hasMessageContaining(String.valueOf(BoundedTicks.MAX_TICKS));
        assertThat(checks.get()).isEqualTo(BoundedTicks.MAX_TICKS + 1);
    }

    @Test
    void aConditionThatClearsWithinTheBudgetReturnsWithoutFailing(Env env) {
        AtomicInteger checks = new AtomicInteger();

        BoundedTicks.tickWhile(env, () -> checks.incrementAndGet() <= 3);

        assertThat(checks.get()).isEqualTo(4);
    }
}
