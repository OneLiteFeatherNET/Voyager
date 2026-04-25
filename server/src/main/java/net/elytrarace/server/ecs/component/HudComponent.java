package net.elytrarace.server.ecs.component;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.game.scoring.MedalTier;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.minestom.server.entity.Player;
import net.minestom.server.sound.SoundEvent;

import java.time.Duration;

/**
 * Owns all in-game HUD state for one player entity: the cup-progress boss bar,
 * actionbar, title overlays, and ring-pass feedback.
 * <p>
 * All methods are called from the tick thread (ECS systems or map-load callbacks
 * that run on the Minestom scheduler thread). There is no concurrent access, so
 * no synchronisation is needed.
 * <p>
 * Call {@link #cleanup()} before removing the entity so the boss bar is hidden.
 */
public class HudComponent implements Component {

    private static final TextColor BRONZE_COLOR = TextColor.color(0xCD7F32);

    private final Player player;
    private BossBar cupProgressBar;

    public HudComponent(Player player) {
        this.player = player;
    }

    /**
     * Sends the live race actionbar: speed, ring progress, elapsed time, and the
     * bracket tier the player is currently on pace for (projected finish bracket).
     *
     * @param speedBlocksPerSec current speed in blocks/s
     * @param passed            rings passed so far
     * @param total             total rings on this map
     * @param elapsedMs         race time elapsed in milliseconds
     * @param pace              projected bracket if the player finished right now
     */
    public void updateActionbar(double speedBlocksPerSec, int passed, int total,
                                long elapsedMs, MedalTier pace) {
        net.kyori.adventure.text.Component timeAndPace = buildTimeAndPace(elapsedMs, pace);
        player.sendActionBar(net.kyori.adventure.text.Component.translatable(
                "hud.actionbar",
                net.kyori.adventure.text.Component.text(String.format("%.1f", speedBlocksPerSec)),
                net.kyori.adventure.text.Component.text(passed),
                net.kyori.adventure.text.Component.text(total),
                timeAndPace));
    }

    /**
     * Sends the post-finish actionbar: speed, medal tier earned, and finish time.
     * Replaces the race actionbar once {@link ScoreComponent#hasFinished()} is true.
     *
     * @param speedBlocksPerSec current speed in blocks/s
     * @param medal             medal tier earned on this map
     * @param finishMs          map completion time in milliseconds
     */
    public void updateActionbarFinished(double speedBlocksPerSec, MedalTier medal, long finishMs) {
        net.kyori.adventure.text.Component medalComponent = net.kyori.adventure.text.Component
                .text(medal.name(), tierColor(medal), TextDecoration.BOLD);
        player.sendActionBar(net.kyori.adventure.text.Component.translatable(
                "hud.actionbar.finished",
                net.kyori.adventure.text.Component.text(String.format("%.1f", speedBlocksPerSec)),
                medalComponent,
                net.kyori.adventure.text.Component.text(formatTime(finishMs))));
    }

    /**
     * Shows or replaces the cup-progress boss bar.
     *
     * @param cupName    current cup name
     * @param currentMap 1-based map index
     * @param totalMaps  total maps in the cup
     */
    public void showCupProgress(String cupName, int currentMap, int totalMaps) {
        if (cupProgressBar != null) {
            player.hideBossBar(cupProgressBar);
        }
        float progress = (float) currentMap / totalMaps;
        cupProgressBar = BossBar.bossBar(
                net.kyori.adventure.text.Component.translatable(
                        "hud.cup_progress",
                        net.kyori.adventure.text.Component.text(cupName),
                        net.kyori.adventure.text.Component.text(currentMap),
                        net.kyori.adventure.text.Component.text(totalMaps)),
                progress,
                BossBar.Color.BLUE,
                BossBar.Overlay.PROGRESS);
        player.showBossBar(cupProgressBar);
    }

    /**
     * Shows the map-name title (fade-in 500 ms, stay 2 s, fade-out 500 ms).
     *
     * @param mapName name to display
     */
    public void showMapTitle(String mapName) {
        player.showTitle(Title.title(
                net.kyori.adventure.text.Component.text(mapName, NamedTextColor.GOLD),
                net.kyori.adventure.text.Component.empty(),
                Title.Times.times(
                        Duration.ofMillis(500),
                        Duration.ofSeconds(2),
                        Duration.ofMillis(500))));
    }

    /**
     * Shows a countdown title. Numbers are color-coded: green for 3, yellow for 2,
     * red for 1, bold red "GO!" for 0.
     *
     * @param seconds remaining seconds (0 → "GO!")
     */
    public void showCountdown(int seconds) {
        net.kyori.adventure.text.Component titleComponent;
        if (seconds > 0) {
            var color = seconds <= 1 ? NamedTextColor.RED
                    : seconds <= 2 ? NamedTextColor.YELLOW
                    : NamedTextColor.GREEN;
            titleComponent = net.kyori.adventure.text.Component.text(
                    String.valueOf(seconds), color, TextDecoration.BOLD);
        } else {
            titleComponent = net.kyori.adventure.text.Component.translatable("hud.countdown.go");
        }
        player.showTitle(Title.title(
                titleComponent,
                net.kyori.adventure.text.Component.empty(),
                Title.Times.times(
                        Duration.ZERO,
                        Duration.ofMillis(900),
                        Duration.ofMillis(100))));
    }

    /**
     * Shows ring-pass feedback: "Ring X/N!" in the actionbar and a pickup sound.
     *
     * @param passed rings passed so far (including this one)
     * @param total  total rings on the map
     */
    public void showRingPassed(int passed, int total) {
        player.sendActionBar(net.kyori.adventure.text.Component.translatable(
                "hud.ring_passed",
                net.kyori.adventure.text.Component.text(passed),
                net.kyori.adventure.text.Component.text(total)));
        player.playSound(Sound.sound(
                SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP,
                Sound.Source.MASTER,
                1.0f,
                1.5f));
    }

    /** Hides the boss bar. Call before removing the entity. */
    public void cleanup() {
        if (cupProgressBar != null) {
            player.hideBossBar(cupProgressBar);
            cupProgressBar = null;
        }
    }

    private static net.kyori.adventure.text.Component buildTimeAndPace(long elapsedMs, MedalTier pace) {
        TextColor color = tierColor(pace);
        return net.kyori.adventure.text.Component.text(
                formatTime(elapsedMs) + " [" + pace.name() + "]", color);
    }

    static TextColor tierColor(MedalTier tier) {
        return switch (tier) {
            case DIAMOND -> NamedTextColor.AQUA;
            case GOLD    -> NamedTextColor.GOLD;
            case SILVER  -> NamedTextColor.GRAY;
            case BRONZE  -> BRONZE_COLOR;
            case FINISH, DNF -> NamedTextColor.RED;
        };
    }

    static String formatTime(long elapsedMs) {
        long seconds = elapsedMs / 1000;
        long tenths  = (elapsedMs % 1000) / 100;
        return String.format("%d:%02d.%d", seconds / 60, seconds % 60, tenths);
    }
}
