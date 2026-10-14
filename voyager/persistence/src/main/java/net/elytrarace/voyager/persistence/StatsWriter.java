package net.elytrarace.voyager.persistence;

import net.elytrarace.voyager.api.stats.CupCompletion;
import net.elytrarace.voyager.api.stats.RaceCompletion;
import net.elytrarace.voyager.api.stats.RaceRecorder;
import net.elytrarace.voyager.persistence.exception.StoreException;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Buffers finished races and cups and writes them to a {@link CompletionStore} in the order they finished.
 *
 * <p>{@link #recordRace} and {@link #recordCup} run on the game loop: they offer to a bounded queue and never
 * wait. {@link #step} and {@link #drain} run on one writer thread ({@link StatsWriterThread}); they are not
 * safe to call from two threads at once. A failed write keeps its completion at the head of the queue and waits
 * for the backoff; a data error drops that completion and the writer goes on.
 */
public final class StatsWriter implements RaceRecorder {

    private final CompletionStore store;
    private final Clock clock;
    private final Backoff backoff;
    private final StatsLog log;
    private final BlockingQueue<Write> queue;
    private final AtomicLong written = new AtomicLong();
    private final AtomicLong retried = new AtomicLong();
    private final AtomicLong droppedFull = new AtomicLong();
    private final AtomicLong droppedData = new AtomicLong();
    private int consecutiveFailures;
    @Nullable
    private Instant nextAttemptAt;
    private boolean outage;

    /**
     * @param store    the database side of the writes
     * @param clock    the source of time for the backoff and the drain limit
     * @param backoff  the wait after each failed attempt
     * @param log      where the warnings and errors go
     * @param capacity the number of completions buffered before the newest is dropped, at least one
     */
    public StatsWriter(CompletionStore store, Clock clock, Backoff backoff, StatsLog log, int capacity) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.backoff = Objects.requireNonNull(backoff, "backoff must not be null");
        this.log = Objects.requireNonNull(log, "log must not be null");
        if (capacity < 1) {
            throw new IllegalArgumentException("queue capacity must be at least one, was %s".formatted(capacity));
        }
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    @Override
    public void recordRace(RaceCompletion completion) {
        offer(new RaceWrite(completion));
    }

    @Override
    public void recordCup(CupCompletion completion) {
        offer(new CupWrite(completion));
    }

    /**
     * Makes one write attempt for the head of the queue, unless the backoff after a failure is still running.
     *
     * @return {@code true} when an attempt was made, {@code false} when the queue is empty or waiting
     */
    public boolean step() {
        Write head = queue.peek();
        if (head == null) {
            return false;
        }
        Instant now = clock.instant();
        if (nextAttemptAt != null && now.isBefore(nextAttemptAt)) {
            return false;
        }
        try {
            head.writeTo(store);
        } catch (RuntimeException failure) {
            onFailure(head, failure, now);
            return true;
        }
        queue.poll();
        written.incrementAndGet();
        consecutiveFailures = 0;
        nextAttemptAt = null;
        reachable();
        return true;
    }

    /**
     * Writes queued completions until the queue is empty, the time limit has passed or a retry is waiting.
     *
     * @param limit how long the drain may run, measured with the writer's clock
     * @return the number of completions still queued
     */
    public int drain(Duration limit) {
        Instant deadline = clock.instant().plus(limit);
        while (clock.instant().isBefore(deadline) && step()) {
            // one write attempt per pass; the condition ends the loop
        }
        return queue.size();
    }

    /** @return the completions waiting in the queue */
    public int queued() {
        return queue.size();
    }

    /** @return the completions written so far */
    public long written() {
        return written.get();
    }

    /** @return the failed attempts that were kept for a later retry */
    public long retried() {
        return retried.get();
    }

    /** @return the completions dropped because the queue was full */
    public long droppedFull() {
        return droppedFull.get();
    }

    /** @return the completions dropped after a data error or an unexpected failure */
    public long droppedData() {
        return droppedData.get();
    }

    private void offer(Write write) {
        if (queue.offer(write)) {
            return;
        }
        long dropped = droppedFull.incrementAndGet();
        log.warn("statistics queue is full, dropped the completion of %s (%s dropped so far)"
                .formatted(write.describe(), dropped));
    }

    private void onFailure(Write head, RuntimeException failure, Instant now) {
        String sqlState = failure instanceof StoreException storeFailure ? storeFailure.sqlState() : null;
        boolean dataError = !(failure instanceof StoreException)
                || SqlStateClassifier.classify(sqlState) == SqlStateClassifier.Failure.DATA;
        if (dataError) {
            queue.poll();
            droppedData.incrementAndGet();
            consecutiveFailures = 0;
            nextAttemptAt = null;
            reachable();
            log.error("dropped the completion of %s, SQLSTATE %s, after one attempt"
                    .formatted(head.describe(), sqlState == null ? "unknown" : sqlState));
            return;
        }
        consecutiveFailures++;
        retried.incrementAndGet();
        nextAttemptAt = now.plus(backoff.delayAfter(consecutiveFailures));
        if (!outage) {
            outage = true;
            log.warn("database unreachable, keeping completions and retrying with backoff; %s queued"
                    .formatted(queue.size()));
        }
    }

    private void reachable() {
        if (outage) {
            outage = false;
            log.warn("database reachable again, %s completions queued".formatted(queue.size()));
        }
    }

    private sealed interface Write permits RaceWrite, CupWrite {

        void writeTo(CompletionStore store);

        String describe();
    }

    private record RaceWrite(RaceCompletion completion) implements Write {

        @Override
        public void writeTo(CompletionStore store) {
            store.writeRace(completion);
        }

        @Override
        public String describe() {
            return completion.cupKey() == null
                    ? "map %s".formatted(completion.mapKey())
                    : "map %s of cup %s".formatted(completion.mapKey(), completion.cupKey());
        }
    }

    private record CupWrite(CupCompletion completion) implements Write {

        @Override
        public void writeTo(CompletionStore store) {
            store.writeCup(completion);
        }

        @Override
        public String describe() {
            return "cup %s".formatted(completion.cupKey());
        }
    }
}
