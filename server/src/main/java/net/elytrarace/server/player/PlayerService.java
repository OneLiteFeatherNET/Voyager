package net.elytrarace.server.player;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.InstanceContainer;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for player lifecycle management: join, leave, teleportation,
 * and online player queries.
 */
public interface PlayerService {

    /**
     * Handles a player joining the server. Sets up the player in the lobby instance
     * with the appropriate game mode.
     *
     * @param player the player who joined
     */
    void onPlayerJoin(Player player);

    /**
     * Handles a player leaving the server. Performs cleanup such as removing the
     * player from active game sessions.
     *
     * @param player the player who left
     */
    void onPlayerLeave(Player player);

    /**
     * Teleports a player to a different instance at the given spawn position.
     * Used for map transitions between lobby, game maps, and other instances.
     *
     * @param player   the player to teleport
     * @param instance the target instance
     * @param spawnPos the position to spawn the player at within the target instance
     * @return a future that completes when the player has fully spawned in the target instance
     */
    CompletableFuture<Void> teleportToInstance(Player player, InstanceContainer instance, Pos spawnPos);

    /**
     * Clears the player's inventory and equips the standard race kit:
     * an elytra in the chestplate slot and firework rockets in hotbar slot 0.
     * Call this after the teleport future completes so packets arrive in order.
     *
     * @param player the player to equip
     */
    void equipForRace(Player player);

    /**
     * Returns all currently online players.
     *
     * @return an unmodifiable collection of online players
     */
    Collection<Player> getOnlinePlayers();

    /**
     * Looks up an online player by UUID.
     *
     * @param uuid the player's unique identifier
     * @return the player if online, or empty
     */
    Optional<Player> getPlayer(UUID uuid);
}
