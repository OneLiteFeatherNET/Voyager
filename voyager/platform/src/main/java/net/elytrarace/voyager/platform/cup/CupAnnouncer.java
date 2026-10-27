package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.MedalBrackets;
import net.elytrarace.voyager.api.race.MedalTier;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.platform.hud.RaceFeedback;
import net.elytrarace.voyager.platform.text.Messages;
import net.elytrarace.voyager.race.cup.CupStanding;
import net.elytrarace.voyager.race.flow.RaceClock;
import net.elytrarace.voyager.race.run.RaceRun;
import net.elytrarace.voyager.race.scoring.CupScore;
import net.elytrarace.voyager.race.scoring.MapScore;
import net.elytrarace.voyager.race.scoring.MedalCountdown;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * What a running cup tells its racers: the ring and finish feedback of a map, the map's result, and the cup's
 * final standings, as chat, titles and log lines.
 *
 * <p>The announcer holds no score and decides no figure. It is given the values it reports, and it sends them to
 * the players the cup supplies.
 */
public final class CupAnnouncer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CupAnnouncer.class);

    /** The sort key standing in for "no finish time": the same stand-in {@code CupStandings} sorts with. */
    private static final Duration NO_TIME_SORTS_LAST = Duration.ofDays(1);

    /** The order of the final standings: most points first, then the faster best time. */
    private static final Comparator<Entry> STANDING_ORDER = Comparator
            .comparingInt(Entry::points).reversed()
            .thenComparing(entry -> entry.bestTime() == null ? NO_TIME_SORTS_LAST : entry.bestTime());

    private final Supplier<Collection<Player>> players;

    /**
     * @param players everyone online, re-read on every call: the roster changes under the tick loop
     */
    public CupAnnouncer(Supplier<Collection<Player>> players) {
        this.players = players;
    }

    /**
     * What a racer is told in the tick a ring was passed, or the tick their run ended.
     *
     * <p>A ring is a <strong>sound</strong> and a green flash on the counter that is already on
     * screen, and nothing else — no chat line and no title. On this course a racer crosses a ring
     * every 1.4 to 2.1 seconds for a minute, so anything per-ring that costs a <em>read</em> is
     * noise by ring five, and thirty-five chat lines is a wall of text that buries the result that
     * follows it.
     */
    public void report(Player racer, MapDefinition map, RaceRun run, boolean wasFinished, RaceClock clock) {
        Ring passed = run.justPassed();
        if (passed != null) {
            RaceFeedback.ringPassed(racer, passed.index(), map.rings().size());
        }
        if (run.finished() && !wasFinished) {
            racer.sendMessage(Messages.finished(map.displayName(), clock.elapsed()));
            LOGGER.info("{} finished '{}' on tick {} ({})",
                    racer.getUsername(), map.name(), clock.gameTick(), seconds(clock.elapsed()));
        }
    }

    /**
     * A map's result, to one racer: a title they can read now that they have landed, and a chat line
     * that is still there when the next map starts.
     *
     * <p>The title region is off limits for the whole race — it is exactly where the next ring
     * appears — which is why it is finally worth something here.
     */
    public void announceMapScore(Player racer, MapDefinition map, @Nullable MapScore score, int ringsPassed) {
        if (score == null) {
            return;
        }
        int ringCount = map.rings().size();
        if (score.medal() == MedalTier.DNF) {
            racer.sendMessage(Messages.mapResultDnf(map.displayName(), ringsPassed, ringCount, score.total()));
            RaceFeedback.mapResult(racer, MedalTier.DNF, Messages.mapResultSubtitleDnf(ringsPassed, ringCount));
            return;
        }
        // The breakdown is in chat only (UX-1): the title and the subtitle carry the medal and the next goal.
        racer.sendMessage(Messages.mapResult(map.displayName(), score.ringPoints(), score.medalPoints(),
                score.medal(), score.placementBonus(), score.total()));
        RaceFeedback.mapResult(racer, score.medal(), nextGoalSubtitle(map, score));
    }

    /**
     * A computer racer's finish of a map, to every human racer: one line, and none for the rings it passed on the way.
     *
     * @param number the computer racer's number, counted from one
     * @param time the time it finished the map in
     */
    public void announceComputerFinish(int number, Duration time) {
        broadcast(Messages.computerRacerFinished(number, time));
    }

    /**
     * The subtitle under a finisher's medal: the seconds to save for the next better medal, or nothing when no better
     * medal is reachable. The medal boundaries are the default brackets, the same ones {@code MapScorer} classifies with.
     */
    private static Component nextGoalSubtitle(MapDefinition map, MapScore score) {
        Duration completion = score.completionTime().orElse(clockLengthOf(map));
        return MedalCountdown.nextGoal(score.medal(), completion, map.referenceTime(), MedalBrackets.DEFAULT)
                .map(goal -> Messages.mapResultNextGoal(goal.saving(), goal.tier()))
                .orElse(Component.empty());
    }

    /**
     * The cup's final standings: the block everybody sees, and one title each saying where they came.
     *
     * <p>The computer racers are in the block too, as {@code COM n} with their fastest finish and no points. Humans
     * and computer racers are placed by one order: points first, then the faster best time. A computer racer never has
     * points, so it comes after every human who has some, and among the humans with none it is placed by its time.
     *
     * @param order the humans' standings, best first, as the cup round reads them
     * @param computerRacers the computer racers that finished a map, in seat order
     */
    public void announceCupResult(CupDefinition cup, List<CupStanding> order,
            List<ComStandings.Standing> computerRacers) {
        List<Entry> entries = new ArrayList<>(order.size() + computerRacers.size());
        for (CupStanding standing : order) {
            CupScore score = standing.score();
            entries.add(new Entry(standing.playerId(), null, score.totalPoints(), score.mapsFinished(),
                    score.bestTime().orElse(null)));
        }
        for (ComStandings.Standing computer : computerRacers) {
            entries.add(new Entry(null, computer, 0, computer.mapsFinished(), computer.bestTime()));
        }
        entries.sort(STANDING_ORDER);
        LOGGER.info("Cup '{}' finished with {} classified racer(s)", cup.name(), entries.size());
        broadcast(Messages.cupHeading(cup.displayName()));
        int mapCount = cup.mapNames().size();
        int place = 1;
        for (Entry entry : entries) {
            if (entry.human() != null) {
                LOGGER.info("  {}. {} — {} point(s), {} map(s) finished, best {}",
                        place, entry.human(), entry.points(), entry.mapsFinished(),
                        Optional.ofNullable(entry.bestTime()).map(CupAnnouncer::seconds).orElse("-"));
                broadcast(Messages.cupRow(place, displayName(entry.human()), entry.points(), entry.mapsFinished(),
                        mapCount, Optional.ofNullable(entry.bestTime())));
                announceCupPlace(entry.human(), place, entry.points());
            } else {
                ComStandings.Standing computer = entry.computer();
                LOGGER.info("  {}. COM {} — {} map(s) finished, best {}",
                        place, computer.number(), entry.mapsFinished(),
                        Optional.ofNullable(entry.bestTime()).map(CupAnnouncer::seconds).orElse("-"));
                broadcast(Messages.cupRow(place, Messages.computerRacerName(computer.number()), 0,
                        entry.mapsFinished(), mapCount, Optional.ofNullable(entry.bestTime())));
            }
            place++;
        }
    }

    /**
     * One line of the final standings: a human from the cup round, or a computer racer from the field's results.
     *
     * @param human the human's id, or {@code null} for a computer racer
     * @param computer the computer racer's standing, or {@code null} for a human
     */
    private record Entry(@Nullable UUID human, @Nullable ComStandings.Standing computer, int points, int mapsFinished,
            @Nullable Duration bestTime) {
    }

    /** The cup title, to the one racer it names, if they are still online to see it. */
    private void announceCupPlace(UUID playerId, int place, int points) {
        for (Player racer : players.get()) {
            if (racer.getUuid().equals(playerId)) {
                RaceFeedback.cupResult(racer, Messages.cupSubtitle(place, points));
                return;
            }
        }
    }

    /**
     * A racer's name for the standings a player reads.
     *
     * <p>Somebody who disconnected before the cup ended is {@code (left)}, not 36 characters of
     * hexadecimal in the middle of a results table. The UUID is still in the log line beside it,
     * where somebody debugging can use it and nobody else has to read it.
     */
    private Component displayName(UUID playerId) {
        for (Player racer : players.get()) {
            if (racer.getUuid().equals(playerId)) {
                return Messages.racerName(racer.getUsername());
            }
        }
        return Messages.departed();
    }

    public void broadcast(Component message) {
        for (Player racer : new ArrayList<>(players.get())) {
            racer.sendMessage(message);
        }
    }

    /**
     * The time to print for a score that somehow carries no completion time on a non-DNF medal.
     *
     * <p>{@code MapScorer} always records one for a finisher, so this is an assertion rather than a
     * branch anybody takes; the map's reference time is the least misleading thing to fall back on.
     */
    private static Duration clockLengthOf(MapDefinition map) {
        return map.referenceTime();
    }

    static String seconds(Duration duration) {
        return "%.3f s".formatted(duration.toNanos() / 1_000_000_000.0);
    }
}
