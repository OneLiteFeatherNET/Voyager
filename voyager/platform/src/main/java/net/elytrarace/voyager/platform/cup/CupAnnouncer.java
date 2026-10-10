package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.MedalTier;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.platform.hud.RaceFeedback;
import net.elytrarace.voyager.platform.text.Messages;
import net.elytrarace.voyager.race.cup.CupStanding;
import net.elytrarace.voyager.race.flow.RaceClock;
import net.elytrarace.voyager.race.run.RaceRun;
import net.elytrarace.voyager.race.scoring.CupScore;
import net.elytrarace.voyager.race.scoring.MapScore;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
            racer.sendMessage(Messages.finished(map.name(), clock.elapsed()));
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
            racer.sendMessage(Messages.mapResultDnf(map.name(), ringsPassed, ringCount, score.total()));
            RaceFeedback.mapResult(racer, MedalTier.DNF,
                    Messages.mapResultSubtitleDnf(ringsPassed, ringCount, score.total()));
            return;
        }
        racer.sendMessage(Messages.mapResult(map.name(), score.ringPoints(), score.medalPoints(),
                score.medal(), score.placementBonus(), score.total()));
        RaceFeedback.mapResult(racer, score.medal(), Messages.mapResultSubtitle(
                score.completionTime().orElse(clockLengthOf(map)), score.total()));
    }

    /**
     * The cup's final standings: the block everybody sees, and one title each saying where they
     * came.
     */
    public void announceCupResult(CupDefinition cup, List<CupStanding> order) {
        LOGGER.info("Cup '{}' finished with {} classified racer(s)", cup.name(), order.size());
        broadcast(Messages.cupHeading(cup.name()));
        int mapCount = cup.mapNames().size();
        int place = 1;
        for (CupStanding standing : order) {
            CupScore score = standing.score();
            LOGGER.info("  {}. {} — {} point(s), {} map(s) finished, best {}",
                    place, standing.playerId(), score.totalPoints(), score.mapsFinished(),
                    score.bestTime().map(CupAnnouncer::seconds).orElse("-"));
            broadcast(Messages.cupRow(place, displayName(standing.playerId()), score.totalPoints(),
                    score.mapsFinished(), mapCount, score.bestTime()));
            announceCupPlace(standing.playerId(), place, score.totalPoints());
            place++;
        }
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
