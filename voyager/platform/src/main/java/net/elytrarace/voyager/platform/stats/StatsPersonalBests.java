package net.elytrarace.voyager.platform.stats;

import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.stats.PlayerProfile;
import net.elytrarace.voyager.api.stats.StatsReader;

import org.jetbrains.annotations.NotNullByDefault;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Reads a racer's stored best through the server's {@link StatsReader}, on a thread of its own so the game loop never waits
 * for the database.
 *
 * <p>A read the executor refuses, or one the store cannot answer, fails its answer, and the place line then says nothing
 * about a best.
 */
@NotNullByDefault
public final class StatsPersonalBests implements PersonalBestReader {

    /** The name of the best-time reader thread, as it appears in dumps and logs. */
    public static final String READER_THREAD_NAME = "voyager-db-best";

    /** Reads that may wait for the one reader thread. Past this a read is refused, and its finish says nothing about a best. */
    static final int QUEUE_CAPACITY = 64;

    private final StatsReader reader;
    private final Executor readers;
    private final Clock clock;

    /**
     * @param reader the store's reader
     * @param readers where each read runs; {@link #readerPool()} in production
     * @param clock the clock the moment of each answer is read from
     */
    public StatsPersonalBests(StatsReader reader, Executor readers, Clock clock) {
        this.reader = reader;
        this.readers = readers;
        this.clock = clock;
    }

    /**
     * The one daemon thread the reads run on, with a bounded queue so a store that hangs cannot grow memory without limit.
     *
     * @return a new pool; the server keeps the one it builds
     */
    public static ThreadPoolExecutor readerPool() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY), task -> {
                    Thread thread = new Thread(task, READER_THREAD_NAME);
                    thread.setDaemon(true);
                    return thread;
                });
    }

    @Override
    public boolean enabled() {
        return reader.recorded();
    }

    @Override
    public BestAnswer bestOn(UUID playerId, String mapKey, String layoutVersion, GameMode mode) {
        BestAnswer answer = new BestAnswer(clock);
        try {
            readers.execute(() -> {
                try {
                    answer.record(bestIn(reader.profile(playerId), mapKey, layoutVersion, mode));
                } catch (RuntimeException failure) {
                    answer.fail();
                }
            });
        } catch (RejectedExecutionException refused) {
            answer.fail();
        }
        return answer;
    }

    /** The stored best of the map, layout and mode in {@code profile}, if one is stored. */
    private static Optional<Duration> bestIn(PlayerProfile profile, String mapKey, String layoutVersion, GameMode mode) {
        return profile.personalBests().stream()
                .filter(best -> best.mapKey().equals(mapKey)
                        && best.layoutVersion().equals(layoutVersion)
                        && best.mode() == mode)
                .findFirst()
                .map(best -> Duration.ofMillis(best.bestTimeMillis()));
    }
}
