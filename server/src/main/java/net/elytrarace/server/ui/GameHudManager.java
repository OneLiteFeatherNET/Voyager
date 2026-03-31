package net.elytrarace.server.ui;

import net.minestom.server.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages {@link GameHud} instances for all active players in a game session.
 *
 * <p>This class provides bulk operations (countdowns, map titles, cup progress)
 * that broadcast to all tracked players, as well as per-player operations like
 * ring-pass feedback and actionbar updates.</p>
 *
 * <p>Thread-safe: the internal map uses {@link ConcurrentHashMap} so players
 * can be added or removed from any thread.</p>
 */
public final class GameHudManager {

    private final Map<UUID, GameHud> huds = new ConcurrentHashMap<>();

    /**
     * Registers a player and creates their HUD. If the player is already
     * registered, their existing HUD is replaced.
     *
     * @param player the player to add
     */
    public void addPlayer(Player player) {
        huds.put(player.getUuid(), new GameHud(player));
    }

    /**
     * Removes a player's HUD and cleans up any active UI elements.
     *
     * @param uuid the UUID of the player to remove
     */
    public void removePlayer(UUID uuid) {
        var hud = huds.remove(uuid);
        if (hud != null) {
            hud.cleanup();
        }
    }

    /**
     * Updates the actionbar for all tracked players with their individual
     * speed and point values. Players present in the HUD map but missing
     * from either input map are silently skipped.
     *
     * @param speeds a map of player UUID to current speed in blocks per second
     * @param points a map of player UUID to current point total
     */
    public void updateAllActionbars(Map<UUID, Double> speeds, Map<UUID, Integer> points) {
        huds.forEach((uuid, hud) -> {
            var speed = speeds.get(uuid);
            var score = points.get(uuid);
            if (speed != null && score != null) {
                hud.updateActionbar(speed, score);
            }
        });
    }

    /**
     * Shows the countdown title to all tracked players.
     *
     * @param seconds the remaining seconds (0 displays "GO!")
     */
    public void showCountdownToAll(int seconds) {
        huds.values().forEach(hud -> hud.showCountdown(seconds));
    }

    /**
     * Shows the map name title to all tracked players.
     *
     * @param mapName the name of the map starting
     */
    public void showMapTitleToAll(String mapName) {
        huds.values().forEach(hud -> hud.showMapTitle(mapName));
    }

    /**
     * Shows ring-pass feedback to a specific player.
     *
     * @param playerId the UUID of the player who passed the ring
     * @param points   the number of points awarded
     */
    public void showRingPassed(UUID playerId, int points) {
        var hud = huds.get(playerId);
        if (hud != null) {
            hud.showRingPassed(points);
        }
    }

    /**
     * Updates the cup progress boss bar for all tracked players.
     *
     * @param cupName the name of the current cup
     * @param current the 1-based index of the current map
     * @param total   the total number of maps in the cup
     */
    public void showCupProgressToAll(String cupName, int current, int total) {
        huds.values().forEach(hud -> hud.showCupProgress(cupName, current, total));
    }

    /**
     * Cleans up all HUD elements for all players and clears the internal registry.
     * Should be called when the game session ends.
     */
    public void cleanup() {
        huds.values().forEach(GameHud::cleanup);
        huds.clear();
    }
}
