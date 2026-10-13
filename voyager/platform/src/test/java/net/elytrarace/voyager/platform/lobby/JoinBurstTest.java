package net.elytrarace.voyager.platform.lobby;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Joins that arrive close together are told as one line. Time is counted in game ticks that the test passes in, so
 * no clock is read and nothing waits.
 */
class JoinBurstTest {

    /** The first join of a burst is sent at once, so a lone join is never held back. */
    @Test
    void oneJoinIsSentAtOnce() {
        JoinBurst joins = new JoinBurst();

        JoinBurst.Outcome outcome = joins.join(0, "Alice");

        assertThat(outcome.sendNow()).isTrue();
        assertThat(outcome.flushed()).isEmpty();
        assertThat(joins.flush(1)).isEmpty();
    }

    /** Joins after the first, inside the window, wait for the window to close and then go out as one line. */
    @Test
    void joinsInsideTheWindowBecomeOneLine() {
        JoinBurst joins = new JoinBurst();
        joins.join(0, "Alice");

        assertThat(joins.join(10, "Bob").sendNow()).isFalse();
        assertThat(joins.join(59, "Carol").sendNow()).isFalse();

        assertThat(joins.flush(60)).as("the window has not closed on its last tick").isEmpty();
        assertThat(joins.flush(61)).containsExactly("Bob", "Carol");
    }

    @Test
    void aJoinAfterTheWindowStartsANewLine() {
        JoinBurst joins = new JoinBurst();
        joins.join(0, "Alice");

        JoinBurst.Outcome later = joins.join(61, "Bob");

        assertThat(later.sendNow()).isTrue();
        assertThat(later.flushed()).as("nothing was held from the old window").isEmpty();
    }

    /** The window is sixty ticks, counted in ticks: a join on the sixtieth tick is inside it, one on the next is not. */
    @Test
    void theWindowIsCountedInTicks() {
        JoinBurst joins = new JoinBurst();
        joins.join(0, "Alice");
        assertThat(joins.join(60, "Bob").sendNow()).as("tick 60 is inside the window").isFalse();

        JoinBurst.Outcome next = joins.join(61, "Carol");

        assertThat(next.sendNow()).as("tick 61 starts a new burst").isTrue();
        assertThat(next.flushed()).as("the held join is told when its window closes").isEqualTo(List.of("Bob"));
    }
}
