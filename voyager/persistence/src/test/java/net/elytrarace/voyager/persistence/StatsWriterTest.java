package net.elytrarace.voyager.persistence;

import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.stats.CupCompletion;
import net.elytrarace.voyager.api.stats.RaceCompletion;
import net.elytrarace.voyager.persistence.exception.StoreException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatsWriterTest {

    private static final Instant START = Instant.parse("2026-10-14T10:00:00Z");
    private static final Duration ONE_SECOND = Duration.ofSeconds(1);

    private final ManualClock clock = new ManualClock(START);
    private final RecordingStatsLog log = new RecordingStatsLog();
    private final Backoff backoff = new ExponentialBackoff(ONE_SECOND, Duration.ofSeconds(60));

    @Test
    void aCompletionIsOfferedWithoutWaiting() {
        FakeCompletionStore store = new FakeCompletionStore(clock);
        StatsWriter writer = new StatsWriter(store, clock, backoff, log, 16);

        writer.recordRace(race("skyfortress"));

        assertThat(writer.queued()).as("the completion waits in the queue").isEqualTo(1);
        assertThat(store.attempts()).as("the caller of recordRace writes nothing").isEmpty();
    }

    @Test
    void aFullQueueDropsTheNewestAndCountsIt() {
        FakeCompletionStore store = new FakeCompletionStore(clock);
        StatsWriter writer = new StatsWriter(store, clock, backoff, log, 2);

        writer.recordRace(race("first"));
        writer.recordRace(race("second"));
        writer.recordRace(race("newest"));

        assertThat(writer.droppedFull()).as("the newest completion is counted as dropped").isEqualTo(1);
        assertThat(writer.queued()).as("the two earlier completions stay queued").isEqualTo(2);
        writer.drain(Duration.ofMinutes(1));
        assertThat(store.written()).as("the queue writes the first two, in order")
                .containsExactly("first", "second");
    }

    @Test
    void aDroppedCompletionIsLoggedOnceWithItsKey() {
        FakeCompletionStore store = new FakeCompletionStore(clock);
        StatsWriter writer = new StatsWriter(store, clock, backoff, log, 1);

        writer.recordRace(race("queued"));
        writer.recordRace(race("newest"));

        assertThat(log.warnings()).as("one warning for the one drop").hasSize(1);
        assertThat(log.warnings().get(0)).as("the warning names the dropped map").contains("newest");
    }

    @Test
    void aLostDatabaseIsRetriedWithBackoffUpToSixtySeconds() {
        FakeCompletionStore store = new FakeCompletionStore(clock);
        store.outage(connectionLost());
        StatsWriter writer = new StatsWriter(store, clock, backoff, log, 16);
        writer.recordRace(race("skyfortress"));

        for (int second = 0; second <= 200; second++) {
            writer.step();
            clock.advance(ONE_SECOND);
        }

        assertThat(gapsInSeconds(store.attempts()))
                .as("the gaps double from one second and stop at the sixty second cap")
                .containsExactly(1L, 2L, 4L, 8L, 16L, 32L, 60L, 60L);
        assertThat(writer.queued()).as("the completion is kept while the database is down").isEqualTo(1);
    }

    @Test
    void theOutageStartAndEndAreLoggedOnceEach() {
        FakeCompletionStore store = new FakeCompletionStore(clock);
        store.outage(connectionLost());
        StatsWriter writer = new StatsWriter(store, clock, backoff, log, 16);
        writer.recordRace(race("skyfortress"));

        for (int second = 0; second < 10; second++) {
            writer.step();
            clock.advance(ONE_SECOND);
        }
        store.recover();
        for (int second = 0; second < 10; second++) {
            writer.step();
            clock.advance(ONE_SECOND);
        }

        assertThat(log.warnings()).as("one line when the outage starts, one when it ends").hasSize(2);
        assertThat(log.warnings().get(0)).contains("unreachable");
        assertThat(log.warnings().get(1)).contains("reachable again");
        assertThat(store.written()).containsExactly("skyfortress");
    }

    @Test
    void aDataErrorDropsOneCompletionAndTheNextIsWritten() {
        FakeCompletionStore store = new FakeCompletionStore(clock);
        store.failNext(new StoreException("check constraint violated", "23514", null));
        StatsWriter writer = new StatsWriter(store, clock, backoff, log, 16);
        writer.recordRace(race("broken"));
        writer.recordRace(race("next"));

        writer.step();
        writer.step();

        assertThat(store.written()).as("the broken completion is not written, the next one is")
                .containsExactly("next");
        assertThat(writer.droppedData()).as("the broken completion is counted as dropped").isEqualTo(1);
        assertThat(log.errors()).as("one error line for the drop").hasSize(1);
        assertThat(log.errors().get(0)).contains("broken");
    }

    @Test
    void completionsAreWrittenInFinishOrder() {
        FakeCompletionStore store = new FakeCompletionStore(clock);
        StatsWriter writer = new StatsWriter(store, clock, backoff, log, 16);
        writer.recordRace(race("first"));
        writer.recordCup(cup("second"));
        writer.recordRace(race("third"));

        writer.step();
        writer.step();
        writer.step();

        assertThat(store.written()).containsExactly("first", "second", "third");
    }

    @Test
    void drainStopsAfterTheTimeLimitAndReportsTheRemainder() {
        FakeCompletionStore store = new FakeCompletionStore(clock, Duration.ofSeconds(4));
        StatsWriter writer = new StatsWriter(store, clock, backoff, log, 16);
        for (int i = 1; i <= 5; i++) {
            writer.recordRace(race("map-" + i));
        }

        int remaining = writer.drain(Duration.ofSeconds(10));

        assertThat(store.written()).as("three writes of four seconds fit into ten seconds").hasSize(3);
        assertThat(remaining).as("the two completions after the limit stay queued").isEqualTo(2);
    }

    @Test
    void theBackoffStartsAgainAtOneSecondAfterASuccessfulWrite() {
        FakeCompletionStore store = new FakeCompletionStore(clock);
        store.outage(connectionLost());
        StatsWriter writer = new StatsWriter(store, clock, backoff, log, 16);
        writer.recordRace(race("first"));
        writer.step();
        clock.advance(ONE_SECOND);
        writer.step();
        clock.advance(Duration.ofSeconds(2));
        store.recover();
        writer.step();
        assertThat(store.written()).containsExactly("first");

        store.outage(connectionLost());
        writer.recordRace(race("second"));
        writer.step();
        clock.advance(ONE_SECOND);
        writer.step();

        List<Instant> attempts = store.attempts();
        assertThat(Duration.between(attempts.get(attempts.size() - 2), attempts.get(attempts.size() - 1)))
                .as("after a success the next outage waits one second, not the grown backoff")
                .isEqualTo(ONE_SECOND);
    }

    private static RaceCompletion race(String mapKey) {
        return new RaceCompletion(mapKey, "0123456789abcdef", GameMode.RACE, null, START, START, 0, List.of());
    }

    private static CupCompletion cup(String cupKey) {
        return new CupCompletion(cupKey, GameMode.RACE, START, START, List.of());
    }

    private static StoreException connectionLost() {
        return new StoreException("connection refused", "08006", null);
    }

    private List<Long> gapsInSeconds(List<Instant> instants) {
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < instants.size(); i++) {
            gaps.add(Duration.between(instants.get(i - 1), instants.get(i)).toSeconds());
        }
        return gaps;
    }
}
