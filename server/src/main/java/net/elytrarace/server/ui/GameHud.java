package net.elytrarace.server.ui;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.minestom.server.entity.Player;
import net.minestom.server.sound.SoundEvent;

import java.time.Duration;

/**
 * Manages the in-game HUD elements for a single player during an elytra race.
 *
 * <p>This includes the actionbar (speed and points), a boss bar for cup progress,
 * title overlays for map names and countdowns, and ring-pass feedback with sound.</p>
 *
 * <p>Instances are created per player and should be cleaned up via {@link #cleanup()}
 * when the player leaves or the game ends.</p>
 */
public final class GameHud {

    private final Player player;
    private BossBar cupProgressBar;

    /**
     * Creates a new HUD bound to the given player.
     *
     * @param player the player this HUD is associated with
     */
    public GameHud(Player player) {
        this.player = player;
    }

    /**
     * Updates the actionbar with the player's current speed and point total.
     *
     * @param speedBlocksPerSec the player's current speed in blocks per second
     * @param currentPoints     the player's accumulated points
     */
    public void updateActionbar(double speedBlocksPerSec, int currentPoints) {
        player.sendActionBar(Component.text(
                String.format("Speed: %.1f m/s | Points: %d", speedBlocksPerSec, currentPoints),
                NamedTextColor.WHITE));
    }

    /**
     * Displays or updates the boss bar showing the player's progress through a cup.
     * Any previously shown cup progress bar is hidden before the new one is displayed.
     *
     * @param cupName   the name of the current cup
     * @param currentMap the 1-based index of the current map within the cup
     * @param totalMaps  the total number of maps in the cup
     */
    public void showCupProgress(String cupName, int currentMap, int totalMaps) {
        if (cupProgressBar != null) {
            player.hideBossBar(cupProgressBar);
        }

        float progress = (float) currentMap / totalMaps;
        cupProgressBar = BossBar.bossBar(
                Component.text(cupName + " - Map " + currentMap + "/" + totalMaps),
                progress,
                BossBar.Color.BLUE,
                BossBar.Overlay.PROGRESS);
        player.showBossBar(cupProgressBar);
    }

    /**
     * Shows a title overlay with the map name when a new map starts.
     * The title fades in over 500ms, stays for 2 seconds, and fades out over 500ms.
     *
     * @param mapName the name of the map to display
     */
    public void showMapTitle(String mapName) {
        player.showTitle(Title.title(
                Component.text(mapName, NamedTextColor.GOLD),
                Component.empty(),
                Title.Times.times(
                        Duration.ofMillis(500),
                        Duration.ofSeconds(2),
                        Duration.ofMillis(500))));
    }

    /**
     * Shows a countdown title overlay. Numbers are color-coded: green for 3,
     * yellow for 2, red for 1, and red bold "GO!" for 0.
     *
     * @param seconds the remaining seconds (0 displays "GO!")
     */
    public void showCountdown(int seconds) {
        var color = seconds <= 1 ? NamedTextColor.RED
                : seconds <= 2 ? NamedTextColor.YELLOW
                : NamedTextColor.GREEN;
        var text = seconds > 0 ? String.valueOf(seconds) : "GO!";

        player.showTitle(Title.title(
                Component.text(text, color, TextDecoration.BOLD),
                Component.empty(),
                Title.Times.times(
                        Duration.ZERO,
                        Duration.ofMillis(900),
                        Duration.ofMillis(100))));
    }

    /**
     * Provides visual and audio feedback when the player passes through a ring.
     * Shows the point gain on the actionbar and plays an experience orb pickup sound.
     *
     * @param points the number of points awarded for passing the ring
     */
    public void showRingPassed(int points) {
        player.sendActionBar(Component.text("+" + points, NamedTextColor.GREEN, TextDecoration.BOLD));
        player.playSound(Sound.sound(
                SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP,
                Sound.Source.MASTER,
                1.0f,
                1.5f));
    }

    /**
     * Removes all HUD elements from the player. Should be called when the player
     * leaves or when the game session ends.
     */
    public void cleanup() {
        if (cupProgressBar != null) {
            player.hideBossBar(cupProgressBar);
            cupProgressBar = null;
        }
    }
}
