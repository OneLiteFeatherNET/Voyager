package net.elytrarace.voyager.persistence;

import net.elytrarace.voyager.api.stats.CupCompletion;
import net.elytrarace.voyager.api.stats.RaceCompletion;
import net.elytrarace.voyager.persistence.exception.StoreException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * An in-memory store. Each write takes {@code writeDuration} of the test clock, so a test can model a slow
 * database; a scripted failure fails one attempt, an outage fails every attempt until {@link #recover()}.
 */
final class FakeCompletionStore implements CompletionStore {

    private final ManualClock clock;
    private final Duration writeDuration;
    private final Deque<StoreException> scripted = new ArrayDeque<>();
    private final List<String> written = new ArrayList<>();
    private final List<Instant> attempts = new ArrayList<>();
    private StoreException outage;

    FakeCompletionStore(ManualClock clock) {
        this(clock, Duration.ZERO);
    }

    FakeCompletionStore(ManualClock clock, Duration writeDuration) {
        this.clock = clock;
        this.writeDuration = writeDuration;
    }

    void failNext(StoreException failure) {
        scripted.add(failure);
    }

    void outage(StoreException failure) {
        this.outage = failure;
    }

    void recover() {
        this.outage = null;
    }

    @Override
    public void writeRace(RaceCompletion completion) {
        attempt(completion.mapKey());
    }

    @Override
    public void writeCup(CupCompletion completion) {
        attempt(completion.cupKey());
    }

    private void attempt(String key) {
        attempts.add(clock.instant());
        clock.advance(writeDuration);
        StoreException failure = scripted.poll();
        if (failure == null) {
            failure = outage;
        }
        if (failure != null) {
            throw failure;
        }
        written.add(key);
    }

    List<String> written() {
        return List.copyOf(written);
    }

    List<Instant> attempts() {
        return List.copyOf(attempts);
    }
}
