package net.elytrarace.voyager.platform.hud;

import net.elytrarace.voyager.platform.text.Messages;
import net.elytrarace.voyager.race.flow.RacePhase;
import net.elytrarace.voyager.race.flow.RaceState;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.race.flow.SecondsUntilGo;
import net.kyori.adventure.text.Component;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.Optional;

/**
 * What the boss bar says while a racer waits between two maps: the count to the next GO, or, for a racer who already stands
 * in the next map before the map before it has ended, how many racers are still on that map.
 *
 * @param title the bar's title
 * @param progress the share of the wait that has passed, from zero to one
 */
@NotNullByDefault
public record WaitBar(Component title, float progress) {

    /**
     * The count to the next GO, from a race state that is in the wait.
     *
     * <p>No wait runs before the first map of a cup, which is the first lobby before any GO, nor after the last map, so
     * neither has a title. A game phase has no wait either: a racer who is still racing has the race bar.
     *
     * @param state the race state
     * @param timings the lengths the cup is played with
     * @param gameStarted whether a map has been started in this cup, which tells the first lobby from a later one
     * @param anotherMapFollows whether the cup has a map after the one that just ended
     * @return the title and progress, or empty when no wait is counted down in this state
     */
    public static Optional<WaitBar> countingDown(RaceState state, RaceTimings timings, boolean gameStarted,
            boolean anotherMapFollows) {
        if (state.cupFinished() || state.phase() == RacePhase.GAME) {
            return Optional.empty();
        }
        if (state.phase() == RacePhase.LOBBY && !gameStarted) {
            return Optional.empty();
        }
        if (state.phase() == RacePhase.END && !anotherMapFollows) {
            return Optional.empty();
        }
        long seconds = SecondsUntilGo.wholeSecondsUp(SecondsUntilGo.remaining(state, timings));
        float progress = (float) SecondsUntilGo.fractionPassed(state, timings);
        return Optional.of(new WaitBar(Messages.waitBossBarNext(seconds), progress));
    }

    /**
     * The title of a racer who already stands in the next map while the map before it still runs for some racers.
     *
     * @param racers how many racers are still on the previous map
     */
    public static WaitBar waiting(int racers) {
        return new WaitBar(Messages.waitBossBarWaiting(racers), 0f);
    }
}
