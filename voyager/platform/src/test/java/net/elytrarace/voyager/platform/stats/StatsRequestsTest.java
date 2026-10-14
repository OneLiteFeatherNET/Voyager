package net.elytrarace.voyager.platform.stats;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The read queue on its own: no server, no database and no sleeps. The reads are queued by hand or run at once, the clock is
 * stepped by the test, and every answer is read back from the tick that sent it.
 */
class StatsRequestsTest {

    private static final Instant START = Instant.parse("2026-10-14T10:00:00Z");

    @Test
    void aLateResultIsDroppedAfterTheDeadlineAnsweredUnavailable() {
        Steps clock = new Steps(START);
        List<Runnable> queued = new ArrayList<>();
        StatsRequests requests = new StatsRequests(queued::add, clock);
        List<Component> replies = new ArrayList<>();
        requests.ask(replies::add, () -> "found", value -> List.of(Component.text(value)));

        clock.advance(StatsRequests.DEADLINE);
        requests.tick();
        queued.forEach(Runnable::run);
        requests.tick();

        assertThat(keys(replies)).as("exactly one answer, the unavailable one").containsExactly("stats.unavailable");
    }

    @Test
    void aTickSendsAtMostEightReplies() {
        StatsRequests requests = new StatsRequests(Runnable::run, new Steps(START));
        List<Component> replies = new ArrayList<>();
        for (int asked = 0; asked < 10; asked++) {
            requests.ask(replies::add, () -> "done", value -> List.of(Component.text(value)));
        }

        requests.tick();
        assertThat(replies).as("the first tick is capped by the budget").hasSize(StatsRequests.REPLIES_PER_TICK);

        requests.tick();
        assertThat(replies).as("the rest follow on the next tick").hasSize(10);
    }

    @Test
    void readsRunOnTheVoyagerDbReadPoolOfTwoThreads() throws Exception {
        ThreadPoolExecutor pool = StatsRequests.readerPool();
        try {
            String thread = pool.submit(() -> Thread.currentThread().getName()).get();

            assertThat(thread).startsWith(StatsRequests.READER_THREAD_PREFIX);
            assertThat(pool.getCorePoolSize()).isEqualTo(StatsRequests.READER_THREADS).isEqualTo(2);
            assertThat(pool.getMaximumPoolSize()).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aRefusedReadAnswersUnavailableOnTheNextTick() {
        Executor full = task -> {
            throw new RejectedExecutionException("the read queue is full");
        };
        StatsRequests requests = new StatsRequests(full, new Steps(START));
        List<Component> replies = new ArrayList<>();
        requests.ask(replies::add, () -> "never read", value -> List.of(Component.text(value)));
        assertThat(replies).as("nothing is sent before the tick").isEmpty();

        requests.tick();

        assertThat(keys(replies)).containsExactly("stats.unavailable");
    }

    private static List<String> keys(List<Component> components) {
        return components.stream().map(component -> ((TranslatableComponent) component).key()).toList();
    }

    /** A clock the test steps by hand. */
    private static final class Steps extends Clock {

        private Instant now;

        Steps(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
