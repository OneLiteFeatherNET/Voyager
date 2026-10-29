package net.elytrarace.voyager.platform.text;

import net.elytrarace.voyager.api.race.MedalTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
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
 *
 * <p>The colour, wording and channel rules that these lines follow are in
 * {@code docs/reference/message-style-guide.md}.
 */
@ApiStatus.Internal
public abstract class Messages {

    private static final String FLIGHT_HUD = "voyager.hud.flight";
    private static final String BOSS_BAR = "voyager.hud.bossbar";
    private static final String BOSS_BAR_LAST_BAND = "voyager.hud.bossbar.last";
    private static final String BOSS_BAR_MEDAL_LEFT = "voyager.hud.medal.left";
    private static final String BOSS_BAR_MEDAL_LAST = "voyager.hud.medal.last";
    private static final String TIER_DIAMOND = "voyager.hud.tier.diamond";
    private static final String TIER_GOLD = "voyager.hud.tier.gold";
    private static final String TIER_SILVER = "voyager.hud.tier.silver";
    private static final String TIER_BRONZE = "voyager.hud.tier.bronze";
    private static final String TIER_FINISH = "voyager.hud.tier.finish";
    private static final String TIER_DNF = "voyager.hud.tier.dnf";
    private static final String COUNTDOWN_DIGIT = "voyager.countdown.digit";
    private static final String COUNTDOWN_GO = "voyager.countdown.go";
    private static final String GO_JUMP = "voyager.race.go.jump";
    private static final String MAP_BANNER = "voyager.map.banner";
    private static final String RACE_FINISHED = "voyager.race.finished";
    private static final String JOINED_MID_CUP = "voyager.race.joined.midcup";
    private static final String LOBBY_ACTIONBAR_WAITING = "voyager.lobby.actionbar.waiting.other";
    private static final String LOBBY_ACTIONBAR_WAITING_ONE = "voyager.lobby.actionbar.waiting.one";
    private static final String LOBBY_ACTIONBAR_COUNTDOWN = "voyager.lobby.actionbar.countdown";
    private static final String LOBBY_SIDEBAR_RACERS = "voyager.lobby.sidebar.racers";
    private static final String LOBBY_SIDEBAR_WAITING = "voyager.lobby.sidebar.status.waiting";
    private static final String LOBBY_SIDEBAR_STARTS = "voyager.lobby.sidebar.status.countdown";
    private static final String LOBBY_LEAVE_REFUSED = "voyager.lobby.leave.refused";
    private static final String LOBBY_ITEM_LEAVE = "voyager.lobby.item.leave";
    private static final String LOBBY_ITEM_LEAVE_LORE = "voyager.lobby.item.leave.lore";
    private static final String LOBBY_ITEM_TUTORIAL = "voyager.lobby.item.tutorial";
    private static final String TUTORIAL_BACK = "voyager.tutorial.button.back";
    private static final String TUTORIAL_NEXT = "voyager.tutorial.button.next";
    private static final String TUTORIAL_CLOSE = "voyager.tutorial.button.close";
    private static final String TUTORIAL_PAGE_1_TITLE = "voyager.tutorial.page.1.title";
    private static final String TUTORIAL_PAGE_2_TITLE = "voyager.tutorial.page.2.title";
    private static final String TUTORIAL_PAGE_3_TITLE = "voyager.tutorial.page.3.title";
    private static final String TUTORIAL_PAGE_4_TITLE = "voyager.tutorial.page.4.title";
    private static final String TUTORIAL_PAGE_5_TITLE = "voyager.tutorial.page.5.title";
    private static final String TUTORIAL_PAGE_1_BODY = "voyager.tutorial.page.1.body";
    private static final String TUTORIAL_PAGE_2_BODY = "voyager.tutorial.page.2.body";
    private static final String TUTORIAL_PAGE_3_BODY = "voyager.tutorial.page.3.body";
    private static final String TUTORIAL_PAGE_4_BODY = "voyager.tutorial.page.4.body";
    private static final String TUTORIAL_PAGE_5_BODY = "voyager.tutorial.page.5.body";
    private static final String TUTORIAL_PAGE_6_TITLE = "voyager.tutorial.page.6.title";
    private static final String TUTORIAL_PAGE_6_BODY = "voyager.tutorial.page.6.body";
    private static final String TUTORIAL_PAGE_6_AUTHORS = "voyager.tutorial.page.6.authors";
    private static final String LOBBY_CANCELLED = "voyager.lobby.cancelled";
    private static final String LOBBY_PLAYER_JOIN = "voyager.lobby.player.join";
    private static final String LOBBY_PLAYERS_JOINED = "voyager.lobby.player.joined.many";
    private static final String RING_MISSED = "voyager.race.ring.missed";
    private static final String COM_RACER_NAME = "voyager.com.racer.name";
    private static final String COM_RACER_FINISHED = "voyager.com.racer.finished";
    private static final String HANDOFF_DISCONNECT = "voyager.handoff.disconnect";
    private static final String LOBBY_BOARD_MAP = "voyager.lobby.board.map";
    private static final String LOBBY_BOARD_MORE = "voyager.lobby.board.more";
    private static final String RESET_TITLE_OUT_OF_BOUNDS = "voyager.reset.title.outofbounds";
    private static final String RESET_TITLE_LANDED = "voyager.reset.title.landed";
    private static final String RESET_TARGET_CHECKPOINT = "voyager.reset.target.checkpoint";
    private static final String RESET_TARGET_START = "voyager.reset.target.start";
    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final String MAP_RESULT_MEDAL = "voyager.result.map.medal";
    private static final String MAP_RESULT_DNF = "voyager.result.map.dnf";
    private static final String MAP_RESULT_SUBTITLE_NEXT = "voyager.result.map.subtitle.next";
    private static final String MAP_RESULT_SUBTITLE_DNF = "voyager.result.map.subtitle.dnf";
    private static final String PLACE_BEHIND = "voyager.result.place.behind";
    private static final String PLACE_AHEAD = "voyager.result.place.ahead";
    private static final String PLACE_ALONE = "voyager.result.place.alone";
    private static final String PB_NEW = "voyager.result.pb.new";
    private static final String PB_FIRST = "voyager.result.pb.first";
    private static final String PB_SLOWER = "voyager.result.pb.slower";
    private static final String PB_EQUAL = "voyager.result.pb.equal";
    private static final String WAIT_BOSS_BAR_NEXT = "voyager.wait.bossbar.next";
    private static final String WAIT_BOSS_BAR_WAITING = "voyager.wait.bossbar.waiting.other";
    private static final String WAIT_BOSS_BAR_WAITING_ONE = "voyager.wait.bossbar.waiting.one";
    private static final String WAIT_SIDEBAR_TITLE = "voyager.wait.sidebar.title";
    private static final String WAIT_SIDEBAR_FINISHER = "voyager.wait.sidebar.finisher";
    private static final String WAIT_SIDEBAR_GAP = "voyager.wait.sidebar.gap";
    private static final String WAIT_SIDEBAR_RACING = "voyager.wait.sidebar.racing";
    private static final String WAIT_SIDEBAR_DNF = "voyager.wait.sidebar.dnf";
    private static final String WAIT_SIDEBAR_MORE = "voyager.wait.sidebar.more";
    private static final String CUP_PLACE_1 = "voyager.cup.place.1.title";
    private static final String CUP_PLACE_2 = "voyager.cup.place.2.title";
    private static final String CUP_PLACE_3 = "voyager.cup.place.3.title";
    private static final String CUP_PLACE_OTHER = "voyager.cup.place.other.title";
    private static final String CUP_PLACE_POINTS = "voyager.cup.place.points";
    private static final String CUP_MAPS = "voyager.result.cup.maps";
    private static final String MAP_INTRO_AUTHORS = "voyager.map.intro.authors";
    private static final String LOBBY_BOARD_MAP_AUTHORS = "voyager.lobby.board.map.authors";
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
    private static final String MEDAL_DIAMOND = "voyager.medal.diamond";
    private static final String MEDAL_GOLD = "voyager.medal.gold";
    private static final String MEDAL_SILVER = "voyager.medal.silver";
    private static final String MEDAL_BRONZE = "voyager.medal.bronze";
    private static final String MEDAL_FINISH = "voyager.medal.finish";
    private static final String MEDAL_DNF = "voyager.medal.dnf";
    private static final String STATS_PROFILE_HEADER = "stats.profile.header";
    private static final String STATS_PROFILE_CUPS = "stats.profile.cups";
    private static final String STATS_PROFILE_WINS = "stats.profile.wins";
    private static final String STATS_PROFILE_MEDALS = "stats.profile.medals";
    private static final String STATS_PROFILE_FLIGHT = "stats.profile.flight";
    private static final String STATS_PROFILE_BEST = "stats.profile.best";
    private static final String STATS_PROFILE_NONE = "stats.profile.none";
    private static final String STATS_UNKNOWN_PLAYER = "stats.unknown_player";
    private static final String STATS_LEADERBOARD_HEADER = "stats.leaderboard.header";
    private static final String STATS_LEADERBOARD_ENTRY = "stats.leaderboard.entry";
    private static final String STATS_LEADERBOARD_EMPTY = "stats.leaderboard.empty";
    private static final String STATS_UNKNOWN_MAP = "stats.unknown_map";
    private static final String STATS_NOT_RECORDED = "stats.not_recorded";
    private static final String STATS_UNAVAILABLE = "stats.unavailable";
    private static final String STATS_USAGE = "stats.usage";
    private static final long MILLIS_PER_HOUR = 3_600_000L;
    private static final long MILLIS_PER_MINUTE = 60_000L;

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
     * <p>The medal is named in words, and the name and the time left are one phrase, coloured with
     * the medal's own colour. The phrase is what tells a racer which band they are in, so the bar
     * itself carries no colour of the medal (see {@link Palette#BOSS_BAR_COLOUR}).
     *
     * <p>{@link MedalTier#FINISH} has no countdown, because there is no band under it to fall into,
     * so it gets the name alone rather than a zero.
     */
    @Contract(pure = true)
    public static Component bossBarTitle(int mapNumber, int mapCount, Component mapName, MedalTier medal,
            Optional<Duration> untilLost) {
        TextColor colour = Palette.of(medal);
        Component tier = Component.translatable(tierKey(medal));
        return untilLost
                .map(left -> Component.translatable(BOSS_BAR,
                        Component.text(mapNumber),
                        Component.text(mapCount),
                        mapName,
                        Component.translatable(BOSS_BAR_MEDAL_LEFT, tier,
                                Component.text(RaceTimeFormat.seconds(left))).color(colour)))
                .orElseGet(() -> Component.translatable(BOSS_BAR_LAST_BAND,
                        Component.text(mapNumber),
                        Component.text(mapCount),
                        mapName,
                        Component.translatable(BOSS_BAR_MEDAL_LAST, tier).color(colour)));
    }

    /** The key of a medal band's name as the boss bar writes it: words, not the capitals the results use. */
    private static String tierKey(MedalTier tier) {
        return switch (tier) {
            case DIAMOND -> TIER_DIAMOND;
            case GOLD -> TIER_GOLD;
            case SILVER -> TIER_SILVER;
            case BRONZE -> TIER_BRONZE;
            case FINISH -> TIER_FINISH;
            case DNF -> TIER_DNF;
        };
    }

    /** One digit of the start countdown. */
    @Contract(pure = true)
    public static Component countdownDigit(int digit) {
        return Component.translatable(COUNTDOWN_DIGIT, Component.text(digit));
    }

    /** The word on the screen at the instant of the launch. */
    @Contract(pure = true)
    public static Component go() {
        return Component.translatable(COUNTDOWN_GO);
    }

    /** The subtitle under GO: how the racer takes off. */
    @Contract(pure = true)
    public static Component goJump() {
        return Component.translatable(GO_JUMP);
    }

    /**
     * The subtitle of a map's intro title: "by" and the authors, joined with commas, in the racer's language.
     *
     * @param authors the authors' names as one line of text
     */
    @Contract(pure = true)
    public static Component mapIntroSubtitle(String authors) {
        return Component.translatable(MAP_INTRO_AUTHORS, Component.text(authors));
    }

    /** The line that announces a map, broadcast when its countdown begins. */
    @Contract(pure = true)
    public static Component mapBanner(int mapNumber, int mapCount, Component mapName, int ringCount,
            Duration referenceTime) {
        return Component.translatable(MAP_BANNER,
                Component.text(mapNumber),
                Component.text(mapCount),
                mapName,
                Component.text(ringCount),
                Component.text(RaceTimeFormat.whole(referenceTime)));
    }

    /** Sent to the one racer who has just crossed the last ring. */
    @Contract(pure = true)
    public static Component finished(Component mapName, Duration time) {
        return Component.translatable(RACE_FINISHED,
                mapName,
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

    /**
     * Sent to every online racer when a racer joins: who joined, how many are online now, and the maximum.
     *
     * <p>The maximum is soft (ADR-0029): a join past it is announced with the count above the maximum, not refused.
     */
    @Contract(pure = true)
    public static Component playerJoined(String name, int online, int maximum) {
        return Component.translatable(LOBBY_PLAYER_JOIN,
                Component.text(name), Component.text(online), Component.text(maximum));
    }

    /**
     * Sent to every online racer for a burst of joins told as one line: how many joined in the window, and how many
     * are online now against the maximum. Used for two or more joins; a single join keeps {@link #playerJoined}.
     *
     * @param joined how many racers joined in the window, two or more
     */
    @Contract(pure = true)
    public static Component playersJoined(int joined, int online, int maximum) {
        return Component.translatable(LOBBY_PLAYERS_JOINED,
                Component.text(joined), Component.text(online), Component.text(maximum));
    }

    /**
     * The action bar of a waiting racer while the minimum is not met: how many racers are still needed to start. One
     * racer short has its own key, so the line reads "Need 1 more player", not "Need 1 more players".
     *
     * @param needed how many racers the cup still needs, at least one
     */
    @Contract(pure = true)
    public static Component lobbyActionBarWaiting(int needed) {
        return Component.translatable(needed == 1 ? LOBBY_ACTIONBAR_WAITING_ONE : LOBBY_ACTIONBAR_WAITING,
                Component.text(needed));
    }

    /**
     * The action bar during the start countdown: the time left in whole seconds, rounded up, so the figure is never zero
     * while the launch is still ahead.
     */
    @Contract(pure = true)
    public static Component lobbyActionBarCountdown(Duration remaining) {
        return Component.translatable(LOBBY_ACTIONBAR_COUNTDOWN, Component.text(wholeSecondsUp(remaining)));
    }

    /**
     * The racers line of the lobby sidebar: how many racers are online, out of the maximum.
     *
     * @param online  how many racers are online
     * @param maximum how many racers make a full server
     */
    @Contract(pure = true)
    public static Component lobbySidebarRacers(int online, int maximum) {
        return Component.translatable(LOBBY_SIDEBAR_RACERS, Component.text(online), Component.text(maximum));
    }

    /** The status line of the lobby sidebar while the minimum is not met. */
    @Contract(pure = true)
    public static Component lobbySidebarStatusWaiting() {
        return Component.translatable(LOBBY_SIDEBAR_WAITING);
    }

    /** The status line of the lobby sidebar during the start countdown: the whole seconds left, rounded up. */
    @Contract(pure = true)
    public static Component lobbySidebarStatusCountdown(Duration remaining) {
        return Component.translatable(LOBBY_SIDEBAR_STARTS, Component.text(wholeSecondsUp(remaining)));
    }

    /** The last line of the lobby sidebar. */
    /** Told to the racers still online when a countdown is cancelled for want of racers. */
    @Contract(pure = true)
    public static Component lobbyCancelled(int online, int needed) {
        return Component.translatable(LOBBY_CANCELLED, Component.text(online), Component.text(needed));
    }

    /**
     * The title of the lobby's cup board: the cup's name. The name is the cup's own, not a sentence the game wrote.
     *
     * @param cup the cup's coloured display name
     */
    @Contract(pure = true)
    public static Component lobbyBoardTitle(Component cup) {
        return cup;
    }

    /**
     * One map line of the lobby's cup board: its place in the cup, its coloured display name and, when they fit on the
     * line, its authors. The name is the map's own, so it is not translated; the place, the layout and "by" are.
     *
     * @param position the map's place in play order, from one
     * @param map      the map's coloured display name
     * @param authors  the authors as one line of text, or {@code null} when the line has none
     */
    @Contract(pure = true)
    public static Component lobbyBoardMap(int position, Component map, @Nullable String authors) {
        if (authors == null) {
            return Component.translatable(LOBBY_BOARD_MAP, Component.text(position), map);
        }
        return Component.translatable(LOBBY_BOARD_MAP_AUTHORS, Component.text(position), map, Component.text(authors));
    }

    /**
     * The last line of the lobby's cup board when the cup has more maps than the board holds: how many follow.
     *
     * @param more how many maps the board does not list
     */
    @Contract(pure = true)
    public static Component lobbyBoardMore(int more) {
        return Component.translatable(LOBBY_BOARD_MORE, Component.text(more));
    }

    /**
     * One map name on the hologram. The hologram is one shared entity, so it carries no translated line: a map's name is
     * the only text it shows, and that is not a sentence the game wrote.
     *
     * @param map the map's display name
     */
    @Contract(pure = true)
    public static Component lobbyHologramName(Component map) {
        return map;
    }

    /** The name of the leave item, the dark oak door in the lobby's hotbar. */
    @Contract(pure = true)
    public static Component leaveItemName() {
        return Component.translatable(LOBBY_ITEM_LEAVE);
    }

    /** The lore line of the leave item: where it sends the racer. */
    @Contract(pure = true)
    public static Component leaveItemLore() {
        return Component.translatable(LOBBY_ITEM_LEAVE_LORE);
    }

    /** The name of the tutorial book in the lobby's hotbar. */
    @Contract(pure = true)
    public static Component tutorialItemName() {
        return Component.translatable(LOBBY_ITEM_TUTORIAL);
    }

    /** Told to a racer who asks to leave while the racer is not waiting in the lobby. */
    @Contract(pure = true)
    public static Component leaveRefused() {
        return Component.translatable(LOBBY_LEAVE_REFUSED);
    }

    /**
     * The title of one tutorial page.
     *
     * @param page the page, counted from one
     */
    @Contract(pure = true)
    public static Component tutorialPageTitle(int page) {
        return Component.translatable(switch (page) {
            case 1 -> TUTORIAL_PAGE_1_TITLE;
            case 2 -> TUTORIAL_PAGE_2_TITLE;
            case 3 -> TUTORIAL_PAGE_3_TITLE;
            case 4 -> TUTORIAL_PAGE_4_TITLE;
            case 5 -> TUTORIAL_PAGE_5_TITLE;
            default -> throw new IllegalArgumentException("the tutorial has pages 1 to 5, not %s".formatted(page));
        });
    }

    /**
     * The body of one tutorial page. Its lines are separated by a newline inside the translation value.
     *
     * @param page the page, counted from one
     */
    @Contract(pure = true)
    public static Component tutorialPageBody(int page) {
        return Component.translatable(switch (page) {
            case 1 -> TUTORIAL_PAGE_1_BODY;
            case 2 -> TUTORIAL_PAGE_2_BODY;
            case 3 -> TUTORIAL_PAGE_3_BODY;
            case 4 -> TUTORIAL_PAGE_4_BODY;
            case 5 -> TUTORIAL_PAGE_5_BODY;
            default -> throw new IllegalArgumentException("the tutorial has pages 1 to 5, not %s".formatted(page));
        });
    }

    /** The Back button of the tutorial. */
    @Contract(pure = true)
    public static Component tutorialBack() {
        return Component.translatable(TUTORIAL_BACK);
    }

    /** The Next button of the tutorial. */
    @Contract(pure = true)
    public static Component tutorialNext() {
        return Component.translatable(TUTORIAL_NEXT);
    }

    /** The sixth tutorial page's title: the cup the racer waits for, in its colour. */
    @Contract(pure = true)
    public static Component tutorialCupTitle(Component cupName) {
        return Component.translatable(TUTORIAL_PAGE_6_TITLE, cupName);
    }

    /** The sixth tutorial page's body: one line per map, in play order, and the cup's authors when it has any. */
    @Contract(pure = true)
    public static Component tutorialCupBody(List<Component> lines) {
        return Component.translatable(TUTORIAL_PAGE_6_BODY, Component.join(JoinConfiguration.newlines(), lines));
    }

    /** One line of the sixth tutorial page: the cup's authors. */
    @Contract(pure = true)
    public static Component tutorialCupAuthors(String authors) {
        return Component.translatable(TUTORIAL_PAGE_6_AUTHORS, Component.text(authors));
    }

    /** The Close button of the tutorial, on every page. */
    @Contract(pure = true)
    public static Component tutorialClose() {
        return Component.translatable(TUTORIAL_CLOSE);
    }

    /** The time left in whole seconds, rounded up and never negative. */
    private static long wholeSecondsUp(Duration remaining) {
        long millis = Math.max(0L, remaining.toMillis());
        return (millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND;
    }

    /**
     * The disconnect a racer gets when no hub handoff runs (a local run without the bridge) or the handoff refuses. The
     * refusal is not retried; the racer reconnects (ADR-0033).
     */
    @Contract(pure = true)
    public static Component handoffDisconnect() {
        return Component.translatable(HANDOFF_DISCONNECT);
    }

    /** A finisher's map result, in one chat line that is still there when the next map starts. */
    @Contract(pure = true)
    public static Component mapResult(Component mapName, int ringPoints, int medalPoints, MedalTier medal,
            int placementBonus, int total) {
        return Component.translatable(MAP_RESULT_MEDAL,
                mapName,
                Component.text(ringPoints),
                Component.text(medalPoints),
                medal(medal),
                Component.text(placementBonus),
                Component.text(total));
    }

    /** A racer who did not finish, told what they did reach rather than only that they failed. */
    @Contract(pure = true)
    public static Component mapResultDnf(Component mapName, int ringsPassed, int ringCount, int points) {
        return Component.translatable(MAP_RESULT_DNF,
                mapName,
                Component.text(ringsPassed),
                Component.text(ringCount),
                Component.text(points));
    }

    /**
     * The subtitle under a finisher's medal: the next goal, in seconds to save, and the medal it buys.
     *
     * @param saving the time to save for {@code next}, one decimal place with a dot
     * @param next the medal the racer would earn
     */
    @Contract(pure = true)
    public static Component mapResultNextGoal(Duration saving, MedalTier next) {
        return Component.translatable(MAP_RESULT_SUBTITLE_NEXT,
                Component.text(RaceTimeFormat.seconds(saving)),
                medal(next));
    }

    /** The subtitle under a DNF: how many of the rings were reached. No points, which are in chat. */
    @Contract(pure = true)
    public static Component mapResultSubtitleDnf(int ringsPassed, int ringCount) {
        return Component.translatable(MAP_RESULT_SUBTITLE_DNF,
                Component.text(ringsPassed),
                Component.text(ringCount));
    }

    /**
     * A finisher's place, behind the racer directly ahead: the place out of every racer who started, and the gap to that
     * racer.
     *
     * @param place the place, counted from one
     * @param of every racer who started the map
     * @param gap the time between the racer and the one ahead
     * @param rival the name of the racer ahead
     */
    @Contract(pure = true)
    public static Component placeBehind(int place, int of, Duration gap, Component rival) {
        return Component.translatable(PLACE_BEHIND,
                Component.text(place), Component.text(of), Component.text(RaceTimeFormat.seconds(gap)), rival);
    }

    /**
     * A first place, ahead of the racer directly behind: the place out of every racer who started, and the gap to that racer.
     *
     * @param place the place, counted from one
     * @param of every racer who started the map
     * @param gap the time between the racer and the one behind
     * @param rival the name of the racer behind
     */
    @Contract(pure = true)
    public static Component placeAhead(int place, int of, Duration gap, Component rival) {
        return Component.translatable(PLACE_AHEAD,
                Component.text(place), Component.text(of), Component.text(RaceTimeFormat.seconds(gap)), rival);
    }

    /**
     * A place with nobody to measure against: the place out of every racer who started, and no other racer named.
     *
     * @param place the place, counted from one
     * @param of every racer who started the map
     */
    @Contract(pure = true)
    public static Component placeAlone(int place, int of) {
        return Component.translatable(PLACE_ALONE, Component.text(place), Component.text(of));
    }

    /**
     * The personal best clause that ends a place line when the finish beats the stored best.
     *
     * @param delta how much faster than the best the finish was
     */
    @Contract(pure = true)
    public static Component personalBestNew(Duration delta) {
        return Component.translatable(PB_NEW, Component.text(RaceTimeFormat.seconds(delta)));
    }

    /**
     * The personal best clause that ends a place line when the finish is slower than the stored best.
     *
     * @param delta how much slower than the best the finish was
     */
    @Contract(pure = true)
    public static Component personalBestSlower(Duration delta) {
        return Component.translatable(PB_SLOWER, Component.text(RaceTimeFormat.seconds(delta)));
    }

    /** The personal best clause that ends a place line when the finish equals the stored best. */
    @Contract(pure = true)
    public static Component personalBestEqual() {
        return Component.translatable(PB_EQUAL);
    }

    /** The personal best clause that ends a place line on a map that has no stored best yet. */
    @Contract(pure = true)
    public static Component personalBestFirst() {
        return Component.translatable(PB_FIRST);
    }

    /**
     * The boss bar's title while a racer waits for the next map: the whole seconds left until its GO, rounded up.
     *
     * @param secondsLeft the seconds left
     */
    @Contract(pure = true)
    public static Component waitBossBarNext(long secondsLeft) {
        return Component.translatable(WAIT_BOSS_BAR_NEXT, Component.text(secondsLeft));
    }

    /**
     * The boss bar's title while a racer already stands in the next map and some racers still race the one before it.
     *
     * @param racers how many racers are still on the previous map
     */
    @Contract(pure = true)
    public static Component waitBossBarWaiting(int racers) {
        return Component.translatable(racers == 1 ? WAIT_BOSS_BAR_WAITING_ONE : WAIT_BOSS_BAR_WAITING,
                Component.text(racers));
    }

    /** The title of the standings sidebar that is shown while a racer waits. */
    @Contract(pure = true)
    public static Component waitStandingsTitle() {
        return Component.translatable(WAIT_SIDEBAR_TITLE);
    }

    /**
     * A finisher's line on the standings sidebar: the place, the name, the time on the map and the gap to the leader.
     *
     * @param place the place, counted from one
     * @param name the racer's name
     * @param time the time the racer finished the map in
     * @param behindLeader the gap to the leader, zero for the leader
     */
    @Contract(pure = true)
    public static Component waitStandingsFinisher(int place, Component name, Duration time, Duration behindLeader) {
        return Component.translatable(WAIT_SIDEBAR_FINISHER,
                Component.text(place), name, Component.text(RaceTimeFormat.tenths(time)),
                Component.translatable(WAIT_SIDEBAR_GAP, Component.text(RaceTimeFormat.seconds(behindLeader))));
    }

    /** A racer still racing the map, on the standings sidebar. */
    @Contract(pure = true)
    public static Component waitStandingsRacing(Component name) {
        return Component.translatable(WAIT_SIDEBAR_RACING, name);
    }

    /** A racer who did not finish the map, on the standings sidebar. */
    @Contract(pure = true)
    public static Component waitStandingsDnf(Component name) {
        return Component.translatable(WAIT_SIDEBAR_DNF, name);
    }

    /**
     * The last line of a standings sidebar that has more racers than lines.
     *
     * @param more how many racers are not listed
     */
    @Contract(pure = true)
    public static Component waitStandingsMore(int more) {
        return Component.translatable(WAIT_SIDEBAR_MORE, Component.text(more));
    }

    /**
     * The action bar line for a ring that was skipped: the ring's number, counted from one.
     *
     * @param ringNumber the missed ring, from one
     */
    @Contract(pure = true)
    public static Component ringMissed(int ringNumber) {
        return Component.translatable(RING_MISSED, Component.text(ringNumber));
    }

    /**
     * The name a computer racer is shown under, above its body and in the tab list: {@code COM n}.
     *
     * @param number the computer racer's number, counted from one
     */
    @Contract(pure = true)
    public static Component computerRacerName(int number) {
        return Component.translatable(COM_RACER_NAME, Component.text(number));
    }

    /**
     * The line sent to every human racer when a computer racer finishes a map: its name and its time.
     *
     * @param number the computer racer's number, counted from one
     * @param time the time it finished the map in
     */
    @Contract(pure = true)
    public static Component computerRacerFinished(int number, Duration time) {
        return Component.translatable(COM_RACER_FINISHED, computerRacerName(number),
                Component.text(RaceTimeFormat.tenths(time)));
    }

    /**
     * The title a racer reads for their place when the cup ends: "Victory!" for first, "2nd place" and "3rd place" for the
     * next two, and "Place n" for every other place.
     */
    @Contract(pure = true)
    public static Component placeTitle(int place) {
        return Component.translatable(switch (place) {
            case 1 -> CUP_PLACE_1;
            case 2 -> CUP_PLACE_2;
            case 3 -> CUP_PLACE_3;
            default -> CUP_PLACE_OTHER;
        }, Component.text(place));
    }

    /** The subtitle under a placement title: the points the racer finished the cup with. */
    @Contract(pure = true)
    public static Component placePoints(int points) {
        return Component.translatable(CUP_PLACE_POINTS, Component.text(points));
    }

    /** The heading above the final standings block, with the cup's coloured display name. */
    @Contract(pure = true)
    public static Component cupHeading(Component cupName) {
        return Component.translatable(CUP_HEADING, cupName);
    }

    /** The line under the standings heading that lists the cup's maps in play order, each in its colour. */
    @Contract(pure = true)
    public static Component cupMaps(List<Component> mapNames) {
        return Component.translatable(CUP_MAPS,
                Component.join(JoinConfiguration.separator(Component.text(", ")), mapNames));
    }

    /**
     * One row of the final standings: the place, in the medal colour for the first three, the name, the points, the best
     * time, the maps finished, and the rings passed over the rings of the whole cup.
     *
     * <p>The viewer's own row is bold and carries a marker, and no other row changes.
     *
     * @param name the racer's name, or {@link #departed()} for somebody who has disconnected — a raw
     *     UUID is 36 characters of hexadecimal in the middle of a results table and tells a reader
     *     nothing they can use
     * @param bestTime their fastest map of the cup, absent if they finished none
     * @param viewer   whether this row is the one the reader is shown
     */
    @Contract(pure = true)
    public static Component cupRow(int place, Component name, int points, Optional<Duration> bestTime,
            int mapsFinished, int mapCount, Optional<Integer> ringsPassed, int ringsTotal, boolean viewer) {
        Component placeText = switch (place) {
            case 1 -> Component.text(place, Palette.GOLD);
            case 2 -> Component.text(place, Palette.SILVER);
            case 3 -> Component.text(place, Palette.BRONZE);
            default -> Component.text(place);
        };
        Component row = Component.translatable(CUP_ROW,
                viewer ? Component.text("» ").append(placeText) : placeText,
                name,
                Component.text(points),
                bestTime.map(time -> (Component) Component.text(RaceTimeFormat.tenths(time)))
                        .orElseGet(() -> Component.translatable(CUP_NO_TIME)),
                Component.text(mapsFinished),
                Component.text(mapCount),
                ringsPassed.<Component>map(Component::text).orElseGet(() -> Component.translatable(CUP_NO_TIME)),
                Component.text(ringsTotal));
        return viewer ? row.decorate(TextDecoration.BOLD) : row;
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
            case DIAMOND -> MEDAL_DIAMOND;
            case GOLD -> MEDAL_GOLD;
            case SILVER -> MEDAL_SILVER;
            case BRONZE -> MEDAL_BRONZE;
            case FINISH -> MEDAL_FINISH;
            case DNF -> MEDAL_DNF;
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
     * @param checkpointNumber the one-based number of the checkpoint the racer is returned to, or empty for a reset
     *     to the start
     */
    public static Component resetSubtitle(OptionalInt checkpointNumber) {
        return checkpointNumber.isPresent()
                ? Component.translatable(RESET_TARGET_CHECKPOINT, Component.text(checkpointNumber.getAsInt()))
                : Component.translatable(RESET_TARGET_START);
    }

    /** The first line of a profile: whose statistics follow. */
    @Contract(pure = true)
    public static Component statsProfileHeader(String name) {
        return Component.translatable(STATS_PROFILE_HEADER, Component.text(name));
    }

    /** Cups the player took part in. */
    @Contract(pure = true)
    public static Component statsProfileCups(int cupsPlayed) {
        return Component.translatable(STATS_PROFILE_CUPS, Component.text(cupsPlayed));
    }

    /** Cups the player won. */
    @Contract(pure = true)
    public static Component statsProfileWins(int cupsWon) {
        return Component.translatable(STATS_PROFILE_WINS, Component.text(cupsWon));
    }

    /** The total of each medal tier over all ranked maps. */
    @Contract(pure = true)
    public static Component statsProfileMedals(int diamonds, int golds, int silvers, int bronzes) {
        return Component.translatable(STATS_PROFILE_MEDALS,
                Component.text(diamonds), Component.text(golds), Component.text(silvers), Component.text(bronzes));
    }

    /**
     * The total gliding time, as hours, minutes and seconds. The seconds are truncated, as every race time is.
     *
     * @param flightMillis the gliding time in milliseconds, never negative
     */
    @Contract(pure = true)
    public static Component statsProfileFlight(long flightMillis) {
        if (flightMillis < 0) {
            throw new IllegalArgumentException("flight time cannot be negative, was %s ms".formatted(flightMillis));
        }
        return Component.translatable(STATS_PROFILE_FLIGHT,
                Component.text(flightMillis / MILLIS_PER_HOUR),
                Component.text(flightMillis / MILLIS_PER_MINUTE % 60),
                Component.text(flightMillis / MILLIS_PER_SECOND % 60));
    }

    /** One personal best of the profile: the map and the fastest time on it. */
    @Contract(pure = true)
    public static Component statsProfileBest(String mapKey, long bestTimeMillis) {
        return Component.translatable(STATS_PROFILE_BEST,
                Component.text(mapKey), Component.text(RaceTimeFormat.millis(bestTimeMillis)));
    }

    /** The profile of a player with no ranked run: there is nothing to show yet. */
    @Contract(pure = true)
    public static Component statsProfileNone(String name) {
        return Component.translatable(STATS_PROFILE_NONE, Component.text(name));
    }

    /** {@code /stats} named a player nobody has recorded under that name. */
    @Contract(pure = true)
    public static Component statsUnknownPlayer(String name) {
        return Component.translatable(STATS_UNKNOWN_PLAYER, Component.text(name));
    }

    /** The first line of a leaderboard: the map it is for. */
    @Contract(pure = true)
    public static Component statsLeaderboardHeader(Component mapName) {
        return Component.translatable(STATS_LEADERBOARD_HEADER, mapName);
    }

    /**
     * One line of a leaderboard.
     *
     * @param rank           the one-based position
     * @param bestTimeMillis the time in milliseconds, never negative
     */
    @Contract(pure = true)
    public static Component statsLeaderboardEntry(int rank, String name, long bestTimeMillis) {
        return Component.translatable(STATS_LEADERBOARD_ENTRY,
                Component.text(rank), Component.text(name), Component.text(RaceTimeFormat.millis(bestTimeMillis)));
    }

    /** The leaderboard of a map that has no recorded time on its current layout. */
    @Contract(pure = true)
    public static Component statsLeaderboardEmpty(Component mapName) {
        return Component.translatable(STATS_LEADERBOARD_EMPTY, mapName);
    }

    /** {@code /leaderboard} named a map the catalogue does not hold. */
    @Contract(pure = true)
    public static Component statsUnknownMap(String mapKey) {
        return Component.translatable(STATS_UNKNOWN_MAP, Component.text(mapKey));
    }

    /** The answer on a server with the statistics switch off. */
    @Contract(pure = true)
    public static Component statsNotRecorded() {
        return Component.translatable(STATS_NOT_RECORDED);
    }

    /** The answer when a read did not finish within two seconds, or the store refused it. */
    @Contract(pure = true)
    public static Component statsUnavailable() {
        return Component.translatable(STATS_UNAVAILABLE);
    }

    /** The usage line, sent when a command is run without the argument it needs. */
    @Contract(pure = true)
    public static Component statsUsage() {
        return Component.translatable(STATS_USAGE);
    }
}
