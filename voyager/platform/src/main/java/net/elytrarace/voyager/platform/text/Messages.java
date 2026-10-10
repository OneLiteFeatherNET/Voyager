package net.elytrarace.voyager.platform.text;

import net.elytrarace.voyager.api.race.MedalTier;
import net.elytrarace.voyager.race.scoring.MedalOutlook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Every sentence the game says to a player, as a translation key with its arguments bound.
 *
 * <h2>The one place a key is named</h2>
 *
 * <p>No call site anywhere else in the rebuild builds a user-facing string. That is not a style
 * preference: a {@code Component.text("Ring 12/35")} deep in a tick loop is a string that can never
 * be translated, never be recoloured and never be found by anybody looking for "where does the game
 * say this". Keys live here, beside the method that uses them, so a change to a message is one edit
 * in one file and a change to a *key* cannot be made in one place and forgotten in another.
 *
 * <h2>Colour belongs to the palette, not to the call site and not to the bundle</h2>
 *
 * <p>Structure and wording live in the bundle, in MiniMessage, coloured with the {@code <c-*>} and
 * {@code <m-*>} tags {@link Palette} defines. The two exceptions are arguments whose colour is
 * <em>state</em> rather than layout — the ring counter, which flashes when a ring is passed, and a
 * medal word, which is coloured by which medal it is. Those arrive as coloured components, built
 * here from the same palette. A translator can still translate every one of these lines; none of
 * them can recolour anything.
 */
@ApiStatus.Internal
public abstract class Messages {

    private static final String FLIGHT_HUD = "voyager.hud.flight";
    private static final String BOSS_BAR = "voyager.hud.bossbar";
    private static final String BOSS_BAR_LAST_BAND = "voyager.hud.bossbar.last";
    private static final String COUNTDOWN_DIGIT = "voyager.countdown.digit";
    private static final String COUNTDOWN_SUBTITLE = "voyager.countdown.subtitle";
    private static final String COUNTDOWN_GO = "voyager.countdown.go";
    private static final String MAP_BANNER = "voyager.map.banner";
    private static final String RACE_FINISHED = "voyager.race.finished";
    private static final String JOINED_MID_CUP = "voyager.race.joined.midcup";
    private static final String LOBBY_WAITING = "voyager.lobby.waiting";
    private static final String LOBBY_COUNTDOWN = "voyager.lobby.countdown";
    private static final String LOBBY_CANCELLED = "voyager.lobby.cancelled";
    private static final String RESET_TITLE_OUT_OF_BOUNDS = "voyager.reset.title.outofbounds";
    private static final String RESET_TITLE_LANDED = "voyager.reset.title.landed";
    private static final String RESET_TARGET_RING = "voyager.reset.target.ring";
    private static final String RESET_TARGET_START = "voyager.reset.target.start";
    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final String MAP_RESULT_MEDAL = "voyager.result.map.medal";
    private static final String MAP_RESULT_DNF = "voyager.result.map.dnf";
    private static final String MAP_RESULT_SUBTITLE = "voyager.result.map.subtitle";
    private static final String MAP_RESULT_SUBTITLE_DNF = "voyager.result.map.subtitle.dnf";
    private static final String CUP_TITLE = "voyager.result.cup.title";
    private static final String CUP_SUBTITLE = "voyager.result.cup.subtitle";
    private static final String CUP_HEADING = "voyager.result.cup.heading";
    private static final String CUP_ROW = "voyager.result.cup.row";
    private static final String CUP_DEPARTED = "voyager.result.cup.departed";
    private static final String CUP_NO_TIME = "voyager.result.cup.notime";
    private static final String COMMAND_RESTARTED = "voyager.command.restarted";
    private static final String COMMAND_SKIP_TAKEN = "voyager.command.skip.taken";
    private static final String COMMAND_SKIP_REFUSED = "voyager.command.skip.refused";
    private static final String COMMAND_RELOAD_PENDING = "voyager.command.reload.pending";
    private static final String COMMAND_RELOAD_REJECTED = "voyager.command.reload.rejected";
    private static final String COMMAND_RELOAD_FAILED = "voyager.command.reload.failed";
    private static final String COMMAND_RELOAD_DENIED = "voyager.command.reload.denied";
    private static final String COMMAND_DENIED = "voyager.command.denied";
    private static final String COMMAND_RELOAD_PROBLEM = "voyager.command.reload.problem";
    private static final String COMMAND_RELOAD_WARNING = "voyager.command.reload.warning";

    private Messages() {
    }

    /**
     * The flight HUD: the race clock and the ring counter, and nothing else.
     *
     * <p>Two fields, not three. A boost bar was designed for the third slot and cut: it duplicates
     * the vanilla item-cooldown sweep the client already draws in the same band of the screen, and at
     * a 30-tick burn against a 40-tick cooldown it would animate a state the racer has no alternative
     * to for three quarters of every cycle.
     *
     * <p>The third argument is a run of spaces as wide as the clock. Adventure centres the whole
     * action-bar string, so with two visible fields it is the gap between them that lands on the
     * screen's centreline; the counter is the field a racer actually checks, so it is padded onto the
     * centreline instead. Minecraft's font is not monospace, so this is close rather than exact —
     * exact needs a resource pack — but it is <em>stable</em>: the pad is the same width every tick,
     * so the HUD never twitches, and a field set that shifts sideways every 1.7 seconds under the
     * crosshair is worse than the information it carries.
     *
     * @param justPassed whether the racer passed a ring in the last few ticks, which turns the
     *     counter green without changing its width
     */
    @Contract(pure = true)
    public static Component flightHud(Duration elapsed, int ringsPassed, int ringCount, boolean justPassed) {
        String clock = RaceTimeFormat.tenths(elapsed);
        TextColor counterColour = justPassed ? Palette.OK : Palette.VALUE;
        return Component.translatable(FLIGHT_HUD,
                Component.text(clock),
                Component.text(ringsPassed, counterColour),
                Component.text(ringCount),
                Component.text(" ".repeat(clock.length())));
    }

    /**
     * The boss bar's title: which map, which map name, and the best medal still reachable together
     * with how long is left to keep it.
     *
     * <p>{@link MedalTier#FINISH} has no countdown, because there is no band under it to fall into,
     * so it gets a shorter line rather than a zero.
     */
    @Contract(pure = true)
    public static Component bossBarTitle(int mapNumber, int mapCount, String mapName, MedalOutlook outlook) {
        Component band = medal(outlook.tier());
        return outlook.untilLost()
                .map(left -> Component.translatable(BOSS_BAR,
                        Component.text(mapNumber),
                        Component.text(mapCount),
                        Component.text(mapName),
                        band,
                        Component.text(RaceTimeFormat.tenths(left), Palette.of(outlook.tier()))))
                .orElseGet(() -> Component.translatable(BOSS_BAR_LAST_BAND,
                        Component.text(mapNumber),
                        Component.text(mapCount),
                        Component.text(mapName),
                        band));
    }

    /** One digit of the start countdown. */
    @Contract(pure = true)
    public static Component countdownDigit(int digit) {
        return Component.translatable(COUNTDOWN_DIGIT, Component.text(digit));
    }

    /** What the racer is told about the course while the countdown runs. */
    @Contract(pure = true)
    public static Component countdownSubtitle(String mapName, int ringCount, Duration referenceTime) {
        return Component.translatable(COUNTDOWN_SUBTITLE,
                Component.text(mapName),
                Component.text(ringCount),
                Component.text(RaceTimeFormat.whole(referenceTime)));
    }

    /** The word on the screen at the instant of the launch. */
    @Contract(pure = true)
    public static Component go() {
        return Component.translatable(COUNTDOWN_GO);
    }

    /** The line that announces a map, broadcast when its countdown begins. */
    @Contract(pure = true)
    public static Component mapBanner(int mapNumber, int mapCount, String mapName, int ringCount,
            Duration referenceTime) {
        return Component.translatable(MAP_BANNER,
                Component.text(mapNumber),
                Component.text(mapCount),
                Component.text(mapName),
                Component.text(ringCount),
                Component.text(RaceTimeFormat.whole(referenceTime)));
    }

    /** Sent to the one racer who has just crossed the last ring. */
    @Contract(pure = true)
    public static Component finished(String mapName, Duration time) {
        return Component.translatable(RACE_FINISHED,
                Component.text(mapName),
                Component.text(RaceTimeFormat.tenths(time)));
    }

    /**
     * Sent to a player who joins while a cup is already running.
     *
     * <p>One string, and it closes a real hole: such a player stands in a racing world with no run,
     * no elytra behaviour and — until this line — no explanation of either.
     */
    @Contract(pure = true)
    public static Component joinedMidCup() {
        return Component.translatable(JOINED_MID_CUP);
    }

    /** What a waiting racer sees while the minimum is not met: how many are online and how many are needed. */
    @Contract(pure = true)
    public static Component lobbyWaiting(int online, int needed) {
        return Component.translatable(LOBBY_WAITING, Component.text(online), Component.text(needed));
    }

    /**
     * What a racer sees while the start countdown runs: the time left in whole seconds, rounded up.
     *
     * <p>Seconds and not ticks: a 20 second lobby is 400 ticks, and a racer shown 400 is shown the unit error
     * issue #101 reported. Rounded up so the figure is never zero while the launch is still ahead.
     */
    @Contract(pure = true)
    public static Component lobbyCountdown(Duration remaining) {
        long millis = Math.max(0L, remaining.toMillis());
        return Component.translatable(LOBBY_COUNTDOWN,
                Component.text((millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND));
    }

    /** Told to the racers still online when a countdown is cancelled for want of racers. */
    @Contract(pure = true)
    public static Component lobbyCancelled(int online, int needed) {
        return Component.translatable(LOBBY_CANCELLED, Component.text(online), Component.text(needed));
    }

    /** A finisher's map result, in one chat line that is still there when the next map starts. */
    @Contract(pure = true)
    public static Component mapResult(String mapName, int ringPoints, int medalPoints, MedalTier medal,
            int placementBonus, int total) {
        return Component.translatable(MAP_RESULT_MEDAL,
                Component.text(mapName),
                Component.text(ringPoints),
                Component.text(medalPoints),
                medal(medal),
                Component.text(placementBonus),
                Component.text(total));
    }

    /** A racer who did not finish, told what they did reach rather than only that they failed. */
    @Contract(pure = true)
    public static Component mapResultDnf(String mapName, int ringsPassed, int ringCount, int points) {
        return Component.translatable(MAP_RESULT_DNF,
                Component.text(mapName),
                Component.text(ringsPassed),
                Component.text(ringCount),
                Component.text(points));
    }

    /** The subtitle under a finisher's medal: the time, and what the map was worth. */
    @Contract(pure = true)
    public static Component mapResultSubtitle(Duration time, int points) {
        return Component.translatable(MAP_RESULT_SUBTITLE,
                Component.text(RaceTimeFormat.tenths(time)),
                Component.text(points));
    }

    /** The subtitle under a DNF: how far they got, and what that was worth. */
    @Contract(pure = true)
    public static Component mapResultSubtitleDnf(int ringsPassed, int ringCount, int points) {
        return Component.translatable(MAP_RESULT_SUBTITLE_DNF,
                Component.text(ringsPassed),
                Component.text(ringCount),
                Component.text(points));
    }

    /** The title shown to everybody when the cup's last map has been scored. */
    @Contract(pure = true)
    public static Component cupTitle() {
        return Component.translatable(CUP_TITLE);
    }

    /** One racer's own line under {@link #cupTitle()}. */
    @Contract(pure = true)
    public static Component cupSubtitle(int place, int points) {
        return Component.translatable(CUP_SUBTITLE, Component.text(place), Component.text(points));
    }

    /** The heading above the final standings block. */
    @Contract(pure = true)
    public static Component cupHeading(String cupName) {
        return Component.translatable(CUP_HEADING, Component.text(cupName));
    }

    /**
     * One row of the final standings.
     *
     * @param name the racer's name, or {@link #departed()} for somebody who has disconnected — a raw
     *     UUID is 36 characters of hexadecimal in the middle of a results table and tells a reader
     *     nothing they can use
     * @param bestTime their fastest map of the cup, absent if they finished none
     */
    @Contract(pure = true)
    public static Component cupRow(int place, Component name, int points, int mapsFinished, int mapCount,
            Optional<Duration> bestTime) {
        return Component.translatable(CUP_ROW,
                Component.text(place),
                name,
                Component.text(points),
                Component.text(mapsFinished),
                Component.text(mapCount),
                bestTime.map(time -> (Component) Component.text(RaceTimeFormat.tenths(time)))
                        .orElseGet(() -> Component.translatable(CUP_NO_TIME)));
    }

    /**
     * A racer's own name, uncoloured so the line it lands in decides how to show it.
     *
     * <p>It exists so that no caller outside this package has to build a text component at all — the
     * one thing that would otherwise force it, a username, is not translatable and so has nowhere
     * else to be made.
     */
    @Contract(pure = true)
    public static Component racerName(String username) {
        return Component.text(username);
    }

    /** What a racer who left before the standings were read out is called. */
    @Contract(pure = true)
    public static Component departed() {
        return Component.translatable(CUP_DEPARTED);
    }

    /** A medal's name, in that medal's colour. */
    @Contract(pure = true)
    public static Component medal(MedalTier tier) {
        return Component.translatable(switch (tier) {
            case DIAMOND -> "voyager.medal.diamond";
            case GOLD -> "voyager.medal.gold";
            case SILVER -> "voyager.medal.silver";
            case BRONZE -> "voyager.medal.bronze";
            case FINISH -> "voyager.medal.finish";
            case DNF -> "voyager.medal.dnf";
        });
    }

    /** {@code /race start}'s answer. */
    @Contract(pure = true)
    public static Component cupRestarted(String cupName) {
        return Component.translatable(COMMAND_RESTARTED, Component.text(cupName));
    }

    /** {@code /race skip}'s answer when a map was actually racing. */
    @Contract(pure = true)
    public static Component skipTaken() {
        return Component.translatable(COMMAND_SKIP_TAKEN);
    }

    /** {@code /race skip}'s answer when nothing was, which is a refusal and not a banked request. */
    @Contract(pure = true)
    public static Component skipRefused() {
        return Component.translatable(COMMAND_SKIP_REFUSED);
    }

    /** {@code /race reload}'s answer when the new catalogue is valid and waits for the next round. */
    @Contract(pure = true)
    public static Component reloadPending() {
        return Component.translatable(COMMAND_RELOAD_PENDING);
    }

    /** {@code /race reload}'s answer when the edit is refused; the problems follow, one message each. */
    @Contract(pure = true)
    public static Component reloadRejected(int problems) {
        return Component.translatable(COMMAND_RELOAD_REJECTED, Component.text(problems));
    }

    /** {@code /race reload}'s answer when the reload itself failed; the cause is in the server log. */
    @Contract(pure = true)
    public static Component reloadFailed() {
        return Component.translatable(COMMAND_RELOAD_FAILED);
    }

    /** One problem of a refused reload, as the line the configuration check prints. */
    @Contract(pure = true)
    public static Component reloadProblem(String line) {
        return Component.translatable(COMMAND_RELOAD_PROBLEM, Component.text(line));
    }

    /** One warning of an applied reload, such as a world that needs a restart. */
    @Contract(pure = true)
    public static Component reloadWarning(String line) {
        return Component.translatable(COMMAND_RELOAD_WARNING, Component.text(line));
    }

    /** The answer to a sender who lacks the permission node a gated command or action needs. */
    @Contract(pure = true)
    public static Component commandDenied() {
        return Component.translatable(COMMAND_DENIED);
    }

    /** {@code /race reload}'s answer to a sender who lacks the reload permission. */
    @Contract(pure = true)
    public static Component reloadDenied() {
        return Component.translatable(COMMAND_RELOAD_DENIED);
    }

    /**
     * The title a racer is shown when a course reset sends them back: why it happened.
     *
     * @param outOfBounds {@code true} for a racer who left the vertical bounds, {@code false} for a landing
     */
    public static Component resetTitle(boolean outOfBounds) {
        return Component.translatable(outOfBounds ? RESET_TITLE_OUT_OF_BOUNDS : RESET_TITLE_LANDED);
    }

    /**
     * The line under a reset title: where the racer is being sent back to.
     *
     * @param ringNumber the one-based number of the last ring passed, or empty for a reset to the start
     */
    public static Component resetSubtitle(OptionalInt ringNumber) {
        return ringNumber.isPresent()
                ? Component.translatable(RESET_TARGET_RING, Component.text(ringNumber.getAsInt()))
                : Component.translatable(RESET_TARGET_START);
    }
}
