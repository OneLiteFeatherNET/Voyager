package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.platform.stats.BestAnswer;
import net.elytrarace.voyager.race.standing.PersonalBestComparison;
import net.elytrarace.voyager.race.standing.PersonalBestComparison.Outcome;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whether a finish may use the stored best that was read at the GO. The clock is stepped by the test and the answer is
 * given by the test, so nothing waits.
 */
class PersonalBestReadTest {

    private static final Instant GO = Instant.parse("2026-10-29T10:00:00Z");

    @Test
    void aFinishAfterTheAnswerComparesWithTheStoredBest() {
        SteppedClock clock = new SteppedClock(GO);
        BestAnswer answer = new BestAnswer(clock);
        PersonalBestRead read = new PersonalBestRead(answer, GO);

        answer.record(Optional.of(Duration.ofMillis(61_200)));

        assertThat(read.compare(Duration.ofMillis(59_100)))
                .as("the answer came back at the GO, and the finish beats the 61.2 s stored best by 2.1 s")
                .contains(new PersonalBestComparison(Outcome.NEW_BEST, Duration.ofMillis(2_100)));
    }

    @Test
    void aFinishBeforeTheAnswerOmitsTheComparison() {
        PersonalBestRead read = new PersonalBestRead(new BestAnswer(new SteppedClock(GO)), GO);

        assertThat(read.compare(Duration.ofMillis(59_100)))
                .as("a read still out at the finish says nothing, and nothing is guessed")
                .isEmpty();
    }

    @Test
    void aLateAnswerIsNotUsed() {
        SteppedClock clock = new SteppedClock(GO);
        BestAnswer answer = new BestAnswer(clock);
        PersonalBestRead read = new PersonalBestRead(answer, GO);

        clock.at(GO.plusSeconds(3));
        answer.record(Optional.of(Duration.ofMillis(61_200)));

        assertThat(read.compare(Duration.ofMillis(59_100)))
                .as("an answer given after the two-second bound is not used, even though it is in before the finish")
                .isEmpty();
    }

    @Test
    void aFailedReadOmitsTheComparison() {
        BestAnswer answer = new BestAnswer(new SteppedClock(GO));
        PersonalBestRead read = new PersonalBestRead(answer, GO);

        answer.fail();

        assertThat(read.compare(Duration.ofMillis(59_100))).isEmpty();
    }

    @Test
    void aMapWithNoStoredBestIsAFirstRun() {
        SteppedClock clock = new SteppedClock(GO);
        BestAnswer answer = new BestAnswer(clock);
        PersonalBestRead read = new PersonalBestRead(answer, GO);

        answer.record(Optional.empty());

        assertThat(read.compare(Duration.ofMillis(59_100)))
                .contains(new PersonalBestComparison(Outcome.FIRST_RUN, Duration.ZERO));
    }

    /** A clock the test moves by hand. */
    private static final class SteppedClock extends Clock {

        private Instant now;

        SteppedClock(Instant now) {
            this.now = now;
        }

        void at(Instant instant) {
            this.now = instant;
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
