package net.elytrarace.voyager.platform.hud;

import net.elytrarace.voyager.platform.text.Messages;
import net.elytrarace.voyager.platform.text.Palette;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The two surfaces a racer reads while flying: the action bar under the crosshair and the boss bar
 * above it.
 *
 * <h2>Why these two and nothing else</h2>
 *
 * <p>At 33 blocks a second the next ring is a 7-block target 40 to 90 blocks out, and the racer's
 * eyes are inside a narrow cone around the flight vector for the whole minute. The action bar is the
 * only text surface inside that cone. The boss bar is above it and is read as a <em>shape</em> — how
 * much is left — which costs nothing when it is ignored.
 *
 * <p>Everything else was left off deliberately. A sidebar scoreboard sits on the right third of the
 * screen, which is where the racing line is through every right-hand turn. A title during a race
 * covers the ring the player is aiming at, because the title region <em>is</em> the flight cone. A
 * speed readout is the worst possible way to feel fast when vanilla already widens the FOV and pitches
 * the elytra sound with velocity, for free, client-side.
 *
 * <h2>One cadence</h2>
 *
 * <p>Both surfaces refresh every {@link #REFRESH_INTERVAL_TICKS} ticks, from one call. Discrete
 * events — a ring passed, the countdown, a result — are not on this clock at all; they go out in the
 * tick that detected them, through {@link RaceFeedback}.
 */
public final class RaceHud {

    /**
     * Ticks between refreshes: two, so 10 Hz.
     *
     * <p>It follows from the clock, not from taste. Both surfaces display tenths of a second, and a
     * tenths display refreshed slower than 10 Hz skips digits — which reads as a stuttering clock,
     * which reads as a stuttering server. Faster than 10 Hz is packets spent on a screen where
     * nothing has changed.
     */
    public static final int REFRESH_INTERVAL_TICKS = 2;

    /**
     * How long the ring counter stays green after a ring.
     *
     * <p>Eight ticks — 400 ms. Long enough to register as an acknowledgement of <em>that</em> ring,
     * and short enough to be gone before the next one: on the committed course a racer crosses a ring
     * every 1.4 to 2.1 seconds, so a flash that outlasted its own ring would be a counter that is
     * simply green for the whole race.
     */
    public static final int RING_FLASH_TICKS = 8;

    /**
     * Solid, never notched. Thirty-five rings divide into none of the notch counts the protocol
     * offers, so a notched bar would put lines on the course where there is nothing — a bar that is
     * precise about something false is worse than one that is only approximate.
     */
    private static final BossBar.Overlay OVERLAY = BossBar.Overlay.PROGRESS;

    /** One bar per racer, because two racers at different rings have different bars to show. */
    private final Map<UUID, BossBar> bars = new HashMap<>();

    /**
     * Puts the boss bar on screen with whatever the state says, without the action bar and without
     * waiting for the cadence.
     *
     * <p>Used during the start countdown, so the map, the target time and the medal on offer are
     * already readable while the racer is still standing on the spawn — the target arrives before the
     * clock starts, not after.
     */
    public void arm(Player racer, HudState state) {
        updateBar(racer, state);
    }

    /**
     * Refreshes both surfaces, if this is a tick they refresh on.
     *
     * @param state this tick's HUD value; {@link HudState#gameTick()} is what the cadence is counted
     *     in, so a caller that passed a stalled tick would freeze the HUD rather than speed it up
     */
    public void render(Player racer, HudState state) {
        if (!refreshesOn(state.gameTick())) {
            return;
        }
        updateBar(racer, state);
        racer.sendActionBar(Messages.flightHud(
                state.elapsed(), state.ringsPassed(), state.ringCount(), flashing(state)));
    }

    /**
     * Takes both surfaces down when the map ends.
     *
     * <p>The action bar is cleared with an empty component rather than left to fade: a stale clock
     * hanging under the crosshair for three seconds after the race ended is a clock that is wrong,
     * and the results are on the screen by then anyway.
     */
    public void standDown(Player racer) {
        BossBar bar = bars.remove(racer.getUuid());
        if (bar != null) {
            racer.hideBossBar(bar);
        }
        racer.sendActionBar(Component.empty());
    }

    /** Drops what was held for a player who disconnected. */
    public void forget(UUID playerId) {
        bars.remove(playerId);
    }

    /**
     * The bar currently on {@code playerId}'s screen, if any.
     *
     * <p>Package-private, and it exists for {@code RaceHudTest}. A boss bar leaves no trace a test
     * can read back: Adventure addresses it to a viewer and Minestom turns it into a packet, so the
     * only other evidence that the fill advanced or the colour changed band is a packet assertion —
     * which asserts the wire format rather than the decision. Not part of this class's contract; do
     * not widen it.
     */
    java.util.Optional<BossBar> barOf(UUID playerId) {
        return java.util.Optional.ofNullable(bars.get(playerId));
    }

    /** Whether the surfaces refresh on this tick. See {@link #REFRESH_INTERVAL_TICKS}. */
    static boolean refreshesOn(int gameTick) {
        return gameTick % REFRESH_INTERVAL_TICKS == 0;
    }

    /**
     * Whether the ring counter is still green.
     *
     * <p>Read out of the state rather than remembered here: {@code RaceRun} already records the tick
     * every ring was passed on, so the flash is a subtraction rather than a second copy of the same
     * fact that can fall out of step with it.
     */
    static boolean flashing(HudState state) {
        return state.lastRingGameTick() > 0
                && state.gameTick() - state.lastRingGameTick() < RING_FLASH_TICKS;
    }

    /** The bar's fill: the fraction of the course behind the racer. */
    static float fill(int ringsPassed, int ringCount) {
        return (float) ringsPassed / ringCount;
    }

    private void updateBar(Player racer, HudState state) {
        Component title = Messages.bossBarTitle(
                state.mapNumber(), state.mapCount(), state.mapName(), state.outlook());
        float progress = fill(state.ringsPassed(), state.ringCount());
        BossBar.Color colour = Palette.bossBarColour(state.outlook().tier());
        BossBar bar = bars.get(racer.getUuid());
        if (bar == null) {
            BossBar created = BossBar.bossBar(title, progress, colour, OVERLAY);
            bars.put(racer.getUuid(), created);
            racer.showBossBar(created);
            return;
        }
        // Adventure's boss bar only sends a packet when a field actually changes, so re-stating an
        // unchanged title twenty times a second costs nothing on the wire.
        bar.name(title);
        bar.progress(progress);
        bar.color(colour);
    }
}
