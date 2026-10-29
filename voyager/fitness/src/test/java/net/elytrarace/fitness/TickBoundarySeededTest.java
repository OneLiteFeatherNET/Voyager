package net.elytrarace.fitness;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import net.elytrarace.voyager.seeded.tick.SeededBlockingWaits;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The tick-boundary rule must catch a blocking wait on a future and must let the non-blocking calls through. Each seeded class is
 * imported on its own, so one test cannot change what another sees.
 */
class TickBoundarySeededTest {

    private static JavaClasses seeded(Class<?> seededClass) {
        return new ClassFileImporter().importClasses(seededClass);
    }

    @Test
    void aJoinOnTheTickIsCaught() {
        assertThatThrownBy(() -> TickBoundaryTest.noBlockingWaitOnAFuture("net.elytrarace.voyager.seeded.tick..")
                .check(seeded(SeededBlockingWaits.JoinOnTheTick.class)))
                .as("a join on the tick is a blocking wait and must be flagged")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("JoinOnTheTick");
    }

    @Test
    void aGetOnTheTickIsCaught() {
        assertThatThrownBy(() -> TickBoundaryTest.noBlockingWaitOnAFuture("net.elytrarace.voyager.seeded.tick..")
                .check(seeded(SeededBlockingWaits.GetOnTheTick.class)))
                .as("a get on the tick is a blocking wait and must be flagged")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("GetOnTheTick");
    }

    @Test
    void aThenRunOnTheTickIsNotCaught() {
        assertThatCode(() -> TickBoundaryTest.noBlockingWaitOnAFuture("net.elytrarace.voyager.seeded.tick..")
                .check(seeded(SeededBlockingWaits.ThenRunOnTheTick.class)))
                .as("thenRun does not wait, so it must not be flagged")
                .doesNotThrowAnyException();
    }

    @Test
    void aWhenCompleteOnTheTickIsNotCaught() {
        assertThatCode(() -> TickBoundaryTest.noBlockingWaitOnAFuture("net.elytrarace.voyager.seeded.tick..")
                .check(seeded(SeededBlockingWaits.WhenCompleteOnTheTick.class)))
                .as("whenComplete does not wait, so it must not be flagged")
                .doesNotThrowAnyException();
    }

    @Test
    void anIsDoneOnTheTickIsNotCaught() {
        assertThatCode(() -> TickBoundaryTest.noBlockingWaitOnAFuture("net.elytrarace.voyager.seeded.tick..")
                .check(seeded(SeededBlockingWaits.IsDoneOnTheTick.class)))
                .as("isDone does not wait, so it must not be flagged")
                .doesNotThrowAnyException();
    }
}
