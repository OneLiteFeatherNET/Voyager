package net.elytrarace.voyager.server;

import io.avaje.inject.BeanScope;

import net.elytrarace.voyager.api.config.DatabaseConfig;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.stats.CupCompletion;
import net.elytrarace.voyager.api.stats.LeaderboardEntry;
import net.elytrarace.voyager.api.stats.RaceCompletion;
import net.elytrarace.voyager.api.stats.RaceRecorder;
import net.elytrarace.voyager.api.stats.PlayerProfile;
import net.elytrarace.voyager.api.stats.StatsReader;
import net.elytrarace.voyager.api.stats.exception.StatsUnavailableException;
import net.elytrarace.voyager.persistence.CompletionStore;
import net.elytrarace.voyager.persistence.StatsLog;
import net.elytrarace.voyager.platform.cup.CupSession;
import net.elytrarace.voyager.platform.stats.NoOpRaceRecorder;
import net.elytrarace.voyager.platform.stats.NoOpStatsReader;
import net.elytrarace.voyager.server.config.PersistenceSettings;
import net.elytrarace.voyager.server.config.ServerSettings;
import net.elytrarace.voyager.server.persistence.OpenResult;
import net.elytrarace.voyager.server.persistence.StatsBinding;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The statistics binding, decided at boot from the persistence settings. The database is never touched: each test hands the
 * binding a fake opener, and the writer is drained explicitly, so no test waits for a thread or a connection. The one graph
 * test runs on a Minestom test environment, which is what the composition root needs to build the cup.
 */
@EnvTest
class PersistenceWiringTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC);
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-00000000c0de");
    private static final String URL = "jdbc:postgresql://db.example.invalid:5432/voyager";

    /** A log that drops its lines: the binding's own warnings are asserted elsewhere, through the settings. */
    private static final StatsLog QUIET = new StatsLog() {
        @Override
        public void warn(String message) {
            // Not under test here.
        }

        @Override
        public void error(String message) {
            // Not under test here.
        }
    };

    @TempDir
    Path tempDir;

    @Test
    void offBindsTheNoOpRecorder() {
        StatsBinding binding = StatsBinding.of(off(), CLOCK, QUIET, neverOpens());

        assertThat(binding.mode()).isEqualTo(StatsBinding.Mode.OFF);
        assertThat(binding.recorder()).isInstanceOf(NoOpRaceRecorder.class);
        assertThat(binding.reader()).isInstanceOf(NoOpStatsReader.class);
        assertThat(binding.bootWarning()).isEmpty();
    }

    @Test
    void offNeverOpensTheDatabase() {
        AtomicInteger opens = new AtomicInteger();

        StatsBinding.of(off(), CLOCK, QUIET, (config, log) -> {
            opens.incrementAndGet();
            return failed("must not be asked");
        });

        assertThat(opens).hasValue(0);
    }

    @Test
    void offAnswersTheReaderThatStatisticsAreNotRecorded() {
        StatsBinding binding = StatsBinding.of(off(), CLOCK, QUIET, neverOpens());

        assertThatThrownBy(() -> binding.reader().profile(PLAYER))
                .isInstanceOf(StatsUnavailableException.class)
                .hasMessage("statistics are not recorded on this server");
    }

    @Test
    void onWithAnAvailableStoreBindsTheStore() {
        FakeStore store = new FakeStore();
        StatsBinding binding = StatsBinding.of(on(), CLOCK, QUIET, (config, log) -> ready(store));

        assertThat(binding.mode()).isEqualTo(StatsBinding.Mode.AVAILABLE);
        assertThat(binding.bootWarning()).isEmpty();
        binding.recorder().recordRace(race());
        binding.drain(Duration.ofSeconds(1));
        assertThat(store.races).hasSize(1);
    }

    @Test
    void onWithAnAvailableStoreAnswersTheReadsFromThatReader() {
        StatsBinding binding = StatsBinding.of(on(), CLOCK, QUIET, (config, log) -> ready(new FakeStore()));

        assertThat(binding.reader().findPlayer("Racer")).contains(PLAYER);
    }

    @Test
    void onWithAnUnavailableDatabaseStillBindsTheWriterAndAnswersUnavailable() {
        StatsBinding binding = StatsBinding.of(on(), CLOCK, QUIET, (config, log) ->
                failed("the database cannot be reached"));

        assertThat(binding.mode()).isEqualTo(StatsBinding.Mode.BUFFERING);
        assertThat(binding.bootWarning()).hasValueSatisfying(warning -> assertThat(warning)
                .contains("buffered and retried").contains("the database cannot be reached"));
        binding.recorder().recordRace(race());
        assertThat(binding.drain(Duration.ofSeconds(1))).as("the completion is kept, not dropped").isEqualTo(1);
        assertThatThrownBy(() -> binding.reader().findPlayer("Racer"))
                .isInstanceOf(StatsUnavailableException.class)
                .hasMessage("statistics are unavailable right now");
    }

    @Test
    void aDatabaseThatOpensOnALaterAttemptStartsReceivingTheBufferedCompletions() {
        FakeStore store = new FakeStore();
        Deque<OpenResult> outcomes = new ArrayDeque<>(List.of(failed("unreachable"), ready(store)));
        StatsBinding binding = StatsBinding.of(on(), CLOCK, QUIET, (config, log) -> outcomes.pop());
        binding.recorder().recordRace(race());

        assertThat(binding.attemptOpen()).isTrue();
        binding.drain(Duration.ofSeconds(1));

        assertThat(store.races).hasSize(1);
    }

    @Test
    void theCupOfAServerWithPersistenceOffRecordsNothing(Env env) throws IOException {
        try (BeanScope scope = VoyagerServer.openGraph(settings(), PersistenceSettings.off())) {
            assertThat(scope.get(RaceRecorder.class)).isInstanceOf(NoOpRaceRecorder.class);
            assertThat(scope.get(CupSession.class)).isNotNull();
        }
    }

    private static DatabaseConfig off() {
        return DatabaseConfig.from(Map.of());
    }

    private static DatabaseConfig on() {
        return DatabaseConfig.from(Map.of(
                DatabaseConfig.KEY_PERSISTENCE, "on",
                DatabaseConfig.KEY_URL, URL,
                DatabaseConfig.KEY_USER, "voyager",
                DatabaseConfig.KEY_PASSWORD, "not-a-real-password"));
    }

    private static StatsBinding.Opener neverOpens() {
        return (config, log) -> {
            throw new AssertionError("this binding must not open a database");
        };
    }

    private static OpenResult ready(FakeStore store) {
        return new OpenResult.Ready(store, new FakeReader());
    }

    private static OpenResult failed(String reason) {
        return new OpenResult.Failed(reason);
    }

    private static RaceCompletion race() {
        Instant start = Instant.parse("2026-10-14T10:00:00Z");
        return new RaceCompletion("alpha", "0123456789abcdef", GameMode.RACE, null, start, start.plusSeconds(60), 0,
                List.of());
    }

    private ServerSettings settings() throws IOException {
        Path data = tempDir.resolve("data");
        Path worlds = tempDir.resolve("worlds");
        Files.createDirectories(worlds);
        ShippedCatalogue.copyMapsInto(data);
        ShippedCatalogue.copyCupsInto(data);
        return new ServerSettings("127.0.0.1", 25570, data, worlds, Optional.of("alpha_cup"), false,
                ServerSettings.PRODUCTION_MINIMUM_RACERS);
    }

    /** A store that keeps what it was given, so a test can count the writes. */
    private static final class FakeStore implements CompletionStore {

        private final List<RaceCompletion> races = new ArrayList<>();

        @Override
        public void writeRace(RaceCompletion completion) {
            races.add(completion);
        }

        @Override
        public void writeCup(CupCompletion completion) {
            // Not written by these tests.
        }
    }

    /** A reader that knows one player. */
    private static final class FakeReader implements StatsReader {

        @Override
        public PlayerProfile profile(UUID playerId) {
            throw new UnsupportedOperationException("not read by these tests");
        }

        @Override
        public Optional<UUID> findPlayer(String name) {
            return Optional.of(PLAYER);
        }

        @Override
        public List<LeaderboardEntry> topTimes(String mapKey, String layoutVersion, GameMode mode, int limit) {
            return List.of();
        }
    }
}
