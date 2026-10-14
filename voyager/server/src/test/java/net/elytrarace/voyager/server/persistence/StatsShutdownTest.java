package net.elytrarace.voyager.server.persistence;

import net.elytrarace.voyager.api.config.DatabaseConfig;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.stats.CupCompletion;
import net.elytrarace.voyager.api.stats.LeaderboardEntry;
import net.elytrarace.voyager.api.stats.PlayerProfile;
import net.elytrarace.voyager.api.stats.RaceCompletion;
import net.elytrarace.voyager.api.stats.StatsReader;
import net.elytrarace.voyager.persistence.CompletionStore;
import net.elytrarace.voyager.persistence.StatsLog;
import net.elytrarace.voyager.persistence.exception.StoreException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The stop path of the statistics binding, with fakes only: a store that records each write, a pool closer that records
 * its call on the same list, and a clock the test controls. No thread is started, so the writes run on the test's thread
 * and the order of the events is the order of the calls.
 */
class StatsShutdownTest {

    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

    private static final StatsLog QUIET = new StatsLog() {
        @Override
        public void warn(String message) {
            // The binding's own log lines are not under test here.
        }

        @Override
        public void error(String message) {
            // The binding's own log lines are not under test here.
        }
    };

    @Test
    void thePoolClosesAfterTheWriterHasDrained() {
        List<String> events = new ArrayList<>();
        StatsBinding binding = bound(new RecordingStore(events, false), events);
        binding.recorder().recordRace(race("alpha"));
        binding.recorder().recordRace(race("beta"));

        binding.shutdown(StatsBinding.SHUTDOWN_DRAIN);

        assertThat(events).containsExactly("written alpha", "written beta", "pool closed");
    }

    @Test
    void shutdownReportsTheCompletionsThatCouldNotBeWritten() {
        List<String> events = new ArrayList<>();
        StatsBinding binding = bound(new RecordingStore(events, true), events);
        binding.recorder().recordRace(race("alpha"));

        int unwritten = binding.shutdown(StatsBinding.SHUTDOWN_DRAIN);

        assertThat(unwritten).as("the database refused the one completion").isEqualTo(1);
        assertThat(events).as("the pool still closes, after the failed attempt").containsExactly("pool closed");
    }

    @Test
    void theDrainStopsAtItsLimitAndReportsTheRest() {
        List<String> events = new ArrayList<>();
        StatsBinding binding = StatsBinding.of(enabled(), new SteppingClock(NOW, Duration.ofSeconds(1)), QUIET,
                (config, log) -> new OpenResult.Ready(new RecordingStore(events, false), new NoReader(),
                        () -> events.add("pool closed")));
        for (int race = 0; race < 10; race++) {
            binding.recorder().recordRace(race("map-" + race));
        }

        int unwritten = binding.shutdown(Duration.ofSeconds(2));

        assertThat(unwritten).as("the rest is reported, not waited for").isPositive();
        assertThat(events.stream().filter(event -> event.startsWith("written")).count())
                .as("what was written plus what was left is every completion")
                .isEqualTo(10 - unwritten);
        assertThat(events).endsWith("pool closed");
    }

    /** A binding over a store that records to {@code events} and a pool closer that records there too. */
    private static StatsBinding bound(CompletionStore store, List<String> events) {
        return StatsBinding.of(enabled(), new FixedClock(NOW), QUIET,
                (config, log) -> new OpenResult.Ready(store, new NoReader(), () -> events.add("pool closed")));
    }

    private static DatabaseConfig enabled() {
        return DatabaseConfig.from(Map.of(
                DatabaseConfig.KEY_PERSISTENCE, "on",
                DatabaseConfig.KEY_URL, "jdbc:postgresql://db.example.invalid:5432/voyager",
                DatabaseConfig.KEY_USER, "voyager",
                DatabaseConfig.KEY_PASSWORD, "not-a-real-password"));
    }

    private static RaceCompletion race(String mapKey) {
        Instant start = Instant.parse("2026-10-14T10:00:00Z");
        return new RaceCompletion(mapKey, "0123456789abcdef", GameMode.RACE, null, start, start.plusSeconds(60), 0,
                List.of());
    }

    /** Records each written race by its map key; when {@code refuse} is set, every write fails like a lost connection. */
    private record RecordingStore(List<String> events, boolean refuse) implements CompletionStore {

        @Override
        public void writeRace(RaceCompletion completion) {
            if (refuse) {
                throw new StoreException("the connection was lost", null, null);
            }
            events.add("written " + completion.mapKey());
        }

        @Override
        public void writeCup(CupCompletion completion) {
            events.add("written cup " + completion.cupKey());
        }
    }

    private static final class NoReader implements StatsReader {

        @Override
        public PlayerProfile profile(UUID playerId) {
            throw new AssertionError("no read is made in these tests");
        }

        @Override
        public Optional<UUID> findPlayer(String name) {
            throw new AssertionError("no read is made in these tests");
        }

        @Override
        public List<LeaderboardEntry> topTimes(String mapKey, String layoutVersion,
                GameMode mode, int limit) {
            throw new AssertionError("no read is made in these tests");
        }
    }

    /** A clock that does not move on its own. */
    private static final class FixedClock extends Clock {

        private final Instant now;

        FixedClock(Instant now) {
            this.now = now;
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

    /** A clock that moves forward by a fixed step on every read, so a drain reaches its limit after a few writes. */
    private static final class SteppingClock extends Clock {

        private Instant now;
        private final Duration step;

        SteppingClock(Instant start, Duration step) {
            this.now = start;
            this.step = step;
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
            Instant current = now;
            now = now.plus(step);
            return current;
        }
    }
}
