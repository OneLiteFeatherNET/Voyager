package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.platform.stats.BestAnswer;
import net.elytrarace.voyager.platform.stats.StatsRequests;
import net.elytrarace.voyager.race.standing.PersonalBestComparison;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * One racer's read of their stored best, started at a map's GO and used at their finish.
 *
 * <p>The answer is used only when it was given within {@link StatsRequests#DEADLINE} of the GO. A read still out at the
 * finish, one that failed, or one that came back late is not used: the place line then says nothing about a best, and
 * nothing is guessed in its place.
 */
final class PersonalBestRead {

    private final BestAnswer answer;
    private final Instant goAt;

    /**
     * @param answer the read's answer, filled off the game loop
     * @param goAt the instant of the map's GO, from which the deadline is counted
     */
    PersonalBestRead(BestAnswer answer, Instant goAt) {
        this.answer = answer;
        this.goAt = goAt;
    }

    /**
     * How the finish stands against the stored best, if the read was answered in time.
     *
     * @param finish the racer's time on the map
     * @return the comparison, or empty when the answer is not usable
     */
    Optional<PersonalBestComparison> compare(Duration finish) {
        if (answer.failed()) {
            return Optional.empty();
        }
        Optional<BestAnswer.Answered> given = answer.answer();
        if (given.isEmpty() || given.get().answeredAt().isAfter(goAt.plus(StatsRequests.DEADLINE))) {
            return Optional.empty();
        }
        return Optional.of(PersonalBestComparison.of(finish, given.get().best().orElse(null)));
    }
}
