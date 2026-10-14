package net.elytrarace.voyager.server.persistence;

import net.elytrarace.voyager.api.config.DatabaseConfig;
import net.elytrarace.voyager.api.stats.RaceRecorder;
import net.elytrarace.voyager.api.stats.StatsReader;
import net.elytrarace.voyager.persistence.ExponentialBackoff;
import net.elytrarace.voyager.persistence.Slf4jStatsLog;
import net.elytrarace.voyager.persistence.StatsLog;
import net.elytrarace.voyager.persistence.StatsStoreFactory;
import net.elytrarace.voyager.persistence.StatsStoreOutcome;
import net.elytrarace.voyager.persistence.StatsWriter;
import net.elytrarace.voyager.persistence.StatsWriterThread;
import net.elytrarace.voyager.platform.stats.NoOpRaceRecorder;
import net.elytrarace.voyager.platform.stats.NoOpStatsReader;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * The statistics the server binds at boot: the recorder the cup calls, the reader the commands call, and the threads that
 * carry the writes to the database.
 *
 * <p>Three modes, decided once by {@link #of}:
 * <ul>
 *   <li>{@link Mode#OFF}: the switch is unset, off, or a database setting is wrong. A no-op recorder and reader; no
 *       connection is attempted and no thread is started.</li>
 *   <li>{@link Mode#AVAILABLE}: the database opened at boot. Completions are written by {@code voyager-db-write}.</li>
 *   <li>{@link Mode#BUFFERING}: the database could not be used at boot. The game starts anyway. Completions wait in the
 *       writer's queue, the writer retries them with a backoff, and {@code voyager-db-open} tries to open the database
 *       with the same backoff (1 s, doubling, capped at 60 s). Reads answer "unavailable" until it opens.</li>
 * </ul>
 *
 * <p>Nothing here runs on the tick. The opening at boot is synchronous, because the server builds its graph at boot; every
 * later attempt runs on {@code voyager-db-open}.
 */
public final class StatsBinding {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatsBinding.class);

    /** The name of the thread that keeps trying to open the database after a failed start. */
    public static final String OPEN_THREAD_NAME = "voyager-db-open";

    /** The wait after the first failed attempt; each further failure doubles it. */
    static final ExponentialBackoff RETRY = new ExponentialBackoff(Duration.ofSeconds(1), Duration.ofSeconds(60));

    /** How the binding was decided at boot. */
    public enum Mode {
        /** No database is used; nothing is recorded. */
        OFF,
        /** The database is open at boot. */
        AVAILABLE,
        /** The database could not be used at boot; the game runs and the database is retried in the background. */
        BUFFERING
    }

    /**
     * One attempt to open the database. The production opener is {@link #databaseOpener(boolean)}; tests pass their own.
     */
    @FunctionalInterface
    public interface Opener {

        /**
         * @param config the database settings; enabled
         * @param log    where the opener's warnings go; the binding passes a log that drops them and reports the reason itself
         * @return the open store and reader, or the reason there is none
         */
        OpenResult open(DatabaseConfig config, StatsLog log);
    }

    /** Drops the opener's own warnings: the binding logs the one line that matters for the mode. */
    private static final StatsLog SILENT = new StatsLog() {
        @Override
        public void warn(String message) {
            // The binding reports the reason once, itself.
        }

        @Override
        public void error(String message) {
            // The binding reports the reason once, itself.
        }
    };

    private final DatabaseConfig config;
    private final Opener opener;
    private final Mode mode;
    private final RaceRecorder recorder;
    private final StatsReader reader;
    @Nullable
    private final StatsWriter writer;
    @Nullable
    private final DeferredCompletionStore deferredStore;
    @Nullable
    private final DeferredStatsReader deferredReader;
    @Nullable
    private final String bootWarning;
    private volatile boolean bound;

    private StatsBinding(DatabaseConfig config, Opener opener, Mode mode, RaceRecorder recorder, StatsReader reader,
            @Nullable StatsWriter writer, @Nullable DeferredCompletionStore deferredStore,
            @Nullable DeferredStatsReader deferredReader, @Nullable String bootWarning) {
        this.config = config;
        this.opener = opener;
        this.mode = mode;
        this.recorder = recorder;
        this.reader = reader;
        this.writer = writer;
        this.deferredStore = deferredStore;
        this.deferredReader = deferredReader;
        this.bootWarning = bootWarning;
    }

    /**
     * Decides the binding. An enabled configuration makes one opening attempt, synchronously, so a database that is up at
     * boot is used from the first completion. A configuration that is not enabled opens nothing.
     *
     * @param config the database settings
     * @param clock  the clock of the writer's backoff and drain limit
     * @param log    where the writer's warnings and errors go
     * @param opener the attempt to open the database
     * @return the binding; it never throws because of the database
     */
    public static StatsBinding of(DatabaseConfig config, Clock clock, StatsLog log, Opener opener) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        Objects.requireNonNull(log, "log must not be null");
        Objects.requireNonNull(opener, "opener must not be null");
        if (!config.enabled()) {
            return new StatsBinding(config, opener, Mode.OFF, new NoOpRaceRecorder(), new NoOpStatsReader(),
                    null, null, null, null);
        }
        DeferredCompletionStore store = new DeferredCompletionStore();
        DeferredStatsReader deferredReader = new DeferredStatsReader();
        StatsWriter writer = new StatsWriter(store, clock, RETRY, log, config.queueCapacity());
        OpenResult first = attempt(opener, config);
        if (first instanceof OpenResult.Ready ready) {
            StatsBinding binding = new StatsBinding(config, opener, Mode.AVAILABLE, writer, deferredReader, writer,
                    store, deferredReader, null);
            binding.bind(ready);
            return binding;
        }
        String reason = ((OpenResult.Failed) first).reason();
        return new StatsBinding(config, opener, Mode.BUFFERING, writer, deferredReader, writer, store, deferredReader,
                "statistics are buffered and retried in the background (up to %s completions): %s; the game runs normally "
                        .formatted(config.queueCapacity(), reason)
                        + "until the database opens");
    }

    /**
     * The production opener: {@link StatsStoreFactory#open} over the pool, with the reader built on the same session factory.
     * The factory's own warning is dropped by the caller's log; a failure comes back as {@link OpenResult.Failed}.
     *
     * @param leakDetection {@code true} in development, {@code false} in production
     * @return the opener
     */
    public static Opener databaseOpener(boolean leakDetection) {
        return (config, log) -> switch (StatsStoreFactory.open(config, leakDetection, log)) {
            case StatsStoreOutcome.Available available -> new OpenResult.Ready(available.store(), available.reader());
            case StatsStoreOutcome.Unavailable unavailable -> new OpenResult.Failed(unavailable.reason());
        };
    }

    /** @return the mode decided at boot */
    public Mode mode() {
        return mode;
    }

    /** @return the recorder the cup calls for every finished map and cup */
    public RaceRecorder recorder() {
        return recorder;
    }

    /** @return the reader the commands call; it answers "unavailable" while the database is not open */
    public StatsReader reader() {
        return reader;
    }

    /**
     * The one warning the boot logs for this mode, when the mode needs one. An {@link Mode#OFF} binding has none here: the
     * settings step reports the switch and every setting problem.
     *
     * @return the warning, or empty
     */
    public Optional<String> bootWarning() {
        return Optional.ofNullable(bootWarning);
    }

    /**
     * Starts the writer thread, and for {@link Mode#BUFFERING} the thread that retries the opening. Called once, at boot,
     * before the server listens. Both threads are daemons.
     */
    public void start() {
        if (writer == null) {
            return;
        }
        new StatsWriterThread(writer).start();
        if (mode == Mode.BUFFERING) {
            Thread opening = new Thread(this::retryOpening, OPEN_THREAD_NAME);
            opening.setDaemon(true);
            opening.start();
        }
    }

    /**
     * One attempt to open the database for a binding that is not open yet. Returns at once when it is open, and does
     * nothing for an {@link Mode#OFF} binding.
     *
     * @return {@code true} when the database is open after this call
     */
    public boolean attemptOpen() {
        if (deferredStore == null || deferredReader == null) {
            return false;
        }
        if (bound) {
            return true;
        }
        OpenResult result = attempt(opener, config);
        if (result instanceof OpenResult.Ready ready) {
            bind(ready);
            if (mode == Mode.BUFFERING) {
                LOGGER.info("statistics database opened; completions are written from now on");
            }
            return true;
        }
        LOGGER.debug("statistics database not open yet: {}", ((OpenResult.Failed) result).reason());
        return false;
    }

    /**
     * Writes the queued completions until the queue is empty, the limit has passed or a retry is waiting. For the drain at
     * shutdown and for tests; the writer thread is the only other caller, and it runs only after {@link #start()}.
     *
     * @param limit how long the drain may run, measured with the binding's clock
     * @return the completions still queued
     */
    public int drain(Duration limit) {
        return writer == null ? 0 : writer.drain(limit);
    }

    private void bind(OpenResult.Ready ready) {
        if (deferredStore != null && deferredReader != null) {
            deferredStore.bind(ready.store());
            deferredReader.bind(ready.reader());
        }
        bound = true;
    }

    private void retryOpening() {
        for (int failures = 1; !bound; failures++) {
            try {
                Thread.sleep(RETRY.delayAfter(failures));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            if (attemptOpen()) {
                return;
            }
        }
    }

    /** One attempt. A failure that escapes the opener is a failed attempt, so the retry thread never dies of it. */
    private static OpenResult attempt(Opener opener, DatabaseConfig config) {
        try {
            return opener.open(config, SILENT);
        } catch (RuntimeException failure) {
            return new OpenResult.Failed("the statistics database could not be opened (%s)"
                    .formatted(failure.getClass().getSimpleName()));
        }
    }

    /** The log the writer uses in production: the server's logging facade, with the writer's own logger name. */
    public static StatsLog writerLog() {
        return new Slf4jStatsLog(LoggerFactory.getLogger(StatsWriter.class));
    }
}
