package net.elytrarace.voyager.race.flow;

import java.time.Duration;

/**
 * How long each {@link RacePhase} lasts before {@link RaceStateMachine#advance} moves past it —
 * except {@code GAME}, which can also end early when every player finishes.
 *
 * <h2>Why {@code END} is two durations and not one</h2>
 *
 * <p>An {@code END} phase between two maps and an {@code END} phase after the last one are different
 * situations wearing the same phase name. Between maps it is a gap: the racer reads three lines and
 * the next map starts, so every second beyond that is a second of not playing. After the last map it
 * is the ceremony the whole cup was for — the standings, the placement, the thing worth looking at.
 * One number is wrong for both, and the tree being replaced picked 100 seconds, which made the
 * results screen longer than the race that earned it.
 *
 * <p>{@link RaceStateMachine#advance} chooses between them by asking whether another map follows,
 * which is the same question it already asks to decide where to go next.
 *
 * @param lobby how long racers wait before a map's {@code GAME} phase begins. The last three seconds
 *     of it are the start countdown, so a lobby shorter than that degrades the countdown rather than
 *     delaying the race — see the platform's start countdown.
 * @param race the cap on a map's {@code GAME} phase; it ends earlier when everybody finishes
 * @param endBetweenMaps the results screen shown when another map follows
 * @param endAfterLastMap the results screen shown when the cup has no map left
 */
public record RaceTimings(Duration lobby, Duration race, Duration endBetweenMaps,
        Duration endAfterLastMap) {

    /**
     * The lengths a production run plays with.
     *
     * <p><strong>Not the old tree's numbers.</strong> Those were lobby 120 s, race 300 s and one
     * 100 s results screen, which wrapped a 36–66 second race in 220 seconds of not playing. The
     * cup is the unit a player experiences, so the cup is what these are chosen against: a
     * three-map cup now runs 20 + 60 + 8 + 60 + 8 + 60 + 20 ≈ 4:16 rather than about eleven
     * minutes, of which three were flying.
     *
     * <ul>
     *   <li><strong>lobby 20 s</strong> — the start countdown. A cup starts only once the minimum
     *       number of racers is online ({@link StartGate}), and then this length is the countdown
     *       that runs before the first map. It is no longer a wait for racers to arrive: a full
     *       lobby starts, an empty one holds, and a drop below the minimum during the countdown
     *       cancels it until its last {@link StartGate#COMMIT_WINDOW}.</li>
     *   <li><strong>race 300 s</strong> — unchanged. The cap only matters to a racer who is lost,
     *       and shortening it is tied to a racer being able to end their own run, which does not
     *       exist yet.</li>
     *   <li><strong>endBetweenMaps 8 s</strong> — long enough to read a medal, a time and a score.</li>
     *   <li><strong>endAfterLastMap 20 s</strong> — the cup standings are worth dwelling on.</li>
     * </ul>
     */
    public static final RaceTimings DEFAULT = new RaceTimings(
            Duration.ofSeconds(20), Duration.ofSeconds(300), Duration.ofSeconds(8), Duration.ofSeconds(20));

    public RaceTimings {
        if (lobby.isNegative() || race.isNegative() || endBetweenMaps.isNegative()
                || endAfterLastMap.isNegative()) {
            throw new IllegalArgumentException(
                    ("race timings must not be negative, was lobby=%s race=%s endBetweenMaps=%s "
                            + "endAfterLastMap=%s")
                            .formatted(lobby, race, endBetweenMaps, endAfterLastMap));
        }
    }

    /**
     * The {@code END} length to play when {@code anotherMapFollows} says whether the cup has a map
     * left after the one that just ended.
     *
     * <p>Here rather than at the call site because it is the whole point of the split: a caller that
     * picked a field by hand is a caller that can pick the wrong one, and there is exactly one
     * question worth asking.
     */
    public Duration end(boolean anotherMapFollows) {
        return anotherMapFollows ? endBetweenMaps : endAfterLastMap;
    }
}
