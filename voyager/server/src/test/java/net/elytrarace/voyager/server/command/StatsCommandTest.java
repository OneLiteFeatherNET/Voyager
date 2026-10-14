package net.elytrarace.voyager.server.command;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.api.stats.LeaderboardEntry;
import net.elytrarace.voyager.api.stats.PlayerProfile;
import net.elytrarace.voyager.api.stats.StatsReader;
import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;
import net.elytrarace.voyager.platform.catalog.LoadedCatalog;
import net.elytrarace.voyager.platform.catalog.writer.LayoutVersion;
import net.elytrarace.voyager.platform.stats.NoOpStatsReader;
import net.elytrarace.voyager.platform.stats.StatsRequests;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.minestom.server.command.ConsoleSender;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code /stats} and {@code /leaderboard} on a Minestom test environment, ticked by hand. The reads run on a thread the test
 * starts and joins, so each read has finished before the tick is called, and no test waits for a clock or a thread. Every
 * answer is recorded with the thread that sent it, which has to be the tick's.
 */
@EnvTest
class StatsCommandTest {

    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");
    private static final UUID RACER = UUID.fromString("00000000-0000-0000-0000-00000000a1ce");
    private static final MapDefinition RIDGE = new MapDefinition("ridge", "ridge-world", new Vec3(0, 64, 0),
            List.of(new Ring(0, new Vec3(0, 64, 10), new Vec3(0, 0, 1), 3, 10, RingType.STANDARD)),
            Duration.ofSeconds(60), new BoostConfig(12, 25), new GuideLine(List.of(), 2, 1.0));

    @Test
    void aReadAnswerIsSentFromTheTickNotFromTheReaderThread(Env env) throws InterruptedException {
        FakeReader reader = new FakeReader();
        reader.players.put("Alex", RACER);
        Harness harness = new Harness(env, reader);
        RecordingSender sender = new RecordingSender();

        env.process().command().execute(sender, "stats Alex");
        harness.readsOnAnotherThread();
        assertThat(sender.messages).as("nothing is sent before the tick").isEmpty();

        harness.tick(env);

        assertThat(sender.messages).as("the profile is sent on the tick").isNotEmpty();
        assertThat(sender.threads).as("every line is sent by the tick's thread")
                .allMatch(thread -> thread == Thread.currentThread());
        assertThat(reader.readThreads).as("the read ran on the reader thread")
                .isNotEmpty()
                .doesNotContain(Thread.currentThread());
    }

    @Test
    void anUnknownPlayerIsToldThereIsNoRecord(Env env) throws InterruptedException {
        Harness harness = new Harness(env, new FakeReader());
        RecordingSender sender = new RecordingSender();

        env.process().command().execute(sender, "stats Nobody");
        harness.readsOnAnotherThread();
        harness.tick(env);

        assertThat(keysOf(sender.messages)).containsExactly("stats.unknown_player");
        assertThat(text(sender.messages.getFirst(), 0)).isEqualTo("Nobody");
    }

    @Test
    void aReadOverTwoSecondsAnswersUnavailable(Env env) {
        Harness harness = new Harness(env, new FakeReader());
        RecordingSender sender = new RecordingSender();

        env.process().command().execute(sender, "stats Alex");
        harness.clock.advance(StatsRequests.DEADLINE);
        harness.tick(env);

        assertThat(keysOf(sender.messages)).containsExactly("stats.unavailable");
    }

    @Test
    void persistenceOffAnswersNotRecorded(Env env) {
        Harness harness = new Harness(env, new NoOpStatsReader());
        RecordingSender sender = new RecordingSender();

        env.process().command().execute(sender, "stats Alex");

        assertThat(keysOf(sender.messages)).as("answered at once, without a read").containsExactly("stats.not_recorded");
        assertThat(harness.queued).as("no read was queued").isEmpty();
    }

    @Test
    void aStatsWithNoArgumentAsksForTheSenderPlayer(Env env) throws InterruptedException {
        FakeReader reader = new FakeReader();
        Harness harness = new Harness(env, reader);
        Player player = connect(env);

        env.process().command().execute(player, "stats");
        harness.readsOnAnotherThread();
        harness.tick(env);

        assertThat(reader.profileRequests).containsExactly(player.getUuid());
    }

    @Test
    void aMapWithNoTimesYetSaysSo(Env env) throws InterruptedException {
        Harness harness = new Harness(env, new FakeReader());
        RecordingSender sender = new RecordingSender();

        env.process().command().execute(sender, "leaderboard ridge");
        harness.readsOnAnotherThread();
        harness.tick(env);

        assertThat(keysOf(sender.messages)).containsExactly("stats.leaderboard.empty");
    }

    @Test
    void theLeaderboardListsAtMostTenEntriesFastestFirst(Env env) throws InterruptedException {
        FakeReader reader = new FakeReader();
        for (int rank = 1; rank <= 12; rank++) {
            reader.times.add(new LeaderboardEntry(rank, UUID.nameUUIDFromBytes(("racer-" + rank).getBytes()),
                    "racer-" + rank, 60_000 + rank * 100, NOW));
        }
        Harness harness = new Harness(env, reader);
        RecordingSender sender = new RecordingSender();

        env.process().command().execute(sender, "leaderboard ridge");
        harness.readsOnAnotherThread();
        harness.tick(env);

        assertThat(reader.lastLimit).as("the reader is asked for ten").isEqualTo(10);
        List<Component> entries = messagesWithKey(sender.messages, "stats.leaderboard.entry");
        assertThat(entries).as("at most ten entries").hasSize(10);
        assertThat(entries).extracting(entry -> text(entry, 0))
                .as("fastest first, by rank")
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
        assertThat(text(entries.getFirst(), 2)).isEqualTo("60.100");
    }

    @Test
    void theLeaderboardReadsTheLayoutOfTheCurrentCatalogueEntry(Env env) throws InterruptedException {
        FakeReader reader = new FakeReader();
        Harness harness = new Harness(env, reader);

        env.process().command().execute(new RecordingSender(), "leaderboard ridge");
        harness.readsOnAnotherThread();
        harness.tick(env);

        assertThat(reader.lastLayout).isEqualTo(LayoutVersion.of(RIDGE));
    }

    @Test
    void anUnknownMapIsSaidSoAtOnce(Env env) {
        Harness harness = new Harness(env, new FakeReader());
        RecordingSender sender = new RecordingSender();

        env.process().command().execute(sender, "leaderboard nowhere");

        assertThat(keysOf(sender.messages)).containsExactly("stats.unknown_map");
        assertThat(harness.queued).isEmpty();
    }

    /** The server's wiring of the two commands: the queue, the catalogue, the reader and the explicit tick. */
    private static final class Harness {

        private final MutableClock clock = new MutableClock(NOW);
        private final List<Runnable> queued = new ArrayList<>();
        private final StatsRequests requests;

        Harness(Env env, StatsReader reader) {
            Executor readers = queued::add;
            requests = new StatsRequests(readers, clock);
            CatalogHolder catalog = new CatalogHolder(catalog());
            env.process().command().register(new StatsCommand(reader, requests));
            env.process().command().register(new LeaderboardCommand(catalog, reader, requests));
        }

        /** Runs every queued read on a thread of its own, and waits for each to finish. */
        void readsOnAnotherThread() throws InterruptedException {
            for (Runnable read : List.copyOf(queued)) {
                Thread thread = new Thread(read, "stats-reader");
                thread.start();
                thread.join();
            }
            queued.clear();
        }

        void tick(Env env) {
            requests.tick();
            env.tick();
        }
    }

    /** Answers from fixed data and records what was asked and on which thread. */
    private static final class FakeReader implements StatsReader {

        private final Map<String, UUID> players = new HashMap<>();
        private final List<LeaderboardEntry> times = new ArrayList<>();
        private final List<Thread> readThreads = new ArrayList<>();
        private final List<UUID> profileRequests = new ArrayList<>();
        private int lastLimit;
        private String lastLayout = "";

        @Override
        public PlayerProfile profile(UUID playerId) {
            readThreads.add(Thread.currentThread());
            profileRequests.add(playerId);
            return new PlayerProfile(playerId, "Alex", 2, 1, 1, 0, 0, 1, 90_000L,
                    List.of(new PlayerProfile.PersonalBest("ridge", LayoutVersion.of(RIDGE), GameMode.RACE, 61_200,
                            NOW)));
        }

        @Override
        public Optional<UUID> findPlayer(String name) {
            readThreads.add(Thread.currentThread());
            return Optional.ofNullable(players.get(name));
        }

        @Override
        public List<LeaderboardEntry> topTimes(String mapKey, String layoutVersion, GameMode mode, int limit) {
            readThreads.add(Thread.currentThread());
            lastLimit = limit;
            lastLayout = layoutVersion;
            return times.stream().limit(limit).toList();
        }
    }

    /** A sender that keeps every line and the thread that sent it. */
    private static final class RecordingSender extends ConsoleSender {

        private final List<Component> messages = new ArrayList<>();
        private final List<Thread> threads = new ArrayList<>();

        @Override
        public void sendMessage(Component message) {
            messages.add(message);
            threads.add(Thread.currentThread());
        }
    }

    /** A clock the test steps by hand. */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant start) {
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

    private static Player connect(Env env) {
        Instance instance = env.createFlatInstance();
        return env.createConnection().connect(instance, new Pos(0, 42, 0));
    }

    private static LoadedCatalog catalog() {
        CupDefinition cup = new CupDefinition("tour", List.of("ridge"), GameMode.RACE);
        return new LoadedCatalog(new CatalogSnapshot(Map.of("ridge", RIDGE), Map.of("tour", cup)), cup, Instant.EPOCH);
    }

    private static List<String> keysOf(List<Component> messages) {
        return messages.stream().map(message -> ((TranslatableComponent) message).key()).toList();
    }

    private static List<Component> messagesWithKey(List<Component> messages, String key) {
        return messages.stream()
                .filter(message -> message instanceof TranslatableComponent translatable && translatable.key().equals(key))
                .toList();
    }

    /** The text of one argument of a translatable line, which the tests read back without rendering it. */
    private static String text(Component message, int argument) {
        Object value = ((TranslatableComponent) message).arguments().get(argument).value();
        return ((TextComponent) ((ComponentLike) value).asComponent()).content();
    }
}
