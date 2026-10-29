package net.elytrarace.voyager.platform.stats;

import org.jetbrains.annotations.NotNullByDefault;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The answer to one read of a stored best: filled once, off the game loop, and read on it without waiting.
 *
 * <p>The tick only ever asks whether an answer is there. It never blocks on the read, so the answer is a plain value that
 * the reader thread publishes, not a future to wait on. The moment of the answer is taken from the clock when it is given,
 * so the tick can tell an answer that came back in time from one that did not.
 */
@NotNullByDefault
public final class BestAnswer {

    /**
     * What the read found.
     *
     * @param best the stored best, or empty when none is stored
     * @param answeredAt when the answer was given
     */
    public record Answered(Optional<Duration> best, Instant answeredAt) {
    }

    private final Clock clock;
    private final AtomicReference<Answered> answered = new AtomicReference<>();
    private final AtomicBoolean failed = new AtomicBoolean();

    /**
     * @param clock the clock the moment of the answer is read from
     */
    public BestAnswer(Clock clock) {
        this.clock = clock;
    }

    /**
     * An answer that never comes: the read is refused, or statistics are off.
     *
     * @return a failed answer
     */
    public static BestAnswer unavailable() {
        BestAnswer answer = new BestAnswer(Clock.systemUTC());
        answer.fail();
        return answer;
    }

    /**
     * Gives the answer. Only the first answer counts.
     *
     * @param best the stored best, or empty when none is stored
     */
    public void record(Optional<Duration> best) {
        answered.compareAndSet(null, new Answered(best, clock.instant()));
    }

    /** Marks the read as failed, so that no answer will come. */
    public void fail() {
        failed.set(true);
    }

    /** The answer, if it has been given. */
    public Optional<Answered> answer() {
        return Optional.ofNullable(answered.get());
    }

    /** Whether the read failed, so that the answer will not come. */
    public boolean failed() {
        return failed.get();
    }
}
