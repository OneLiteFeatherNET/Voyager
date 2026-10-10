package net.elytrarace.game.system;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.common.ecs.System;
import net.elytrarace.game.player.components.PlayerPositionsComponent;
import net.elytrarace.game.util.PluginInstanceHolder;
import org.apache.commons.geometry.euclidean.threed.Vector3D;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * System that updates player entities with their current positions.
 */
public class PlayerUpdateSystem implements System {
    
    private final Map<UUID, Vector3D[]> playerPositionsCache = new HashMap<>();
    
    @Override
    public Set<Class<? extends Component>> getRequiredComponents() {
        return Set.of();
    }
    
    @Override
    public void process(Entity entity, float deltaTime) {
        // This system doesn't process entities directly
    }
    
    /**
     * Updates all player entities with their current positions.
     * This should be called every tick.
     */
    public void updateAllPlayers() {
        EntityManager entityManager = PluginInstanceHolder.getEntityManager();
        
        // Update positions for all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            
            // Get or create position history
            Vector3D[] positions = playerPositionsCache.computeIfAbsent(playerId, k -> new Vector3D[3]);
            
            // Shift positions
            if (positions[1] != null) {
                positions[2] = positions[1];
            }
            if (positions[0] != null) {
                positions[1] = positions[0];
            }
            
            // Add current position
            Location location = player.getLocation();
            positions[0] = Vector3D.of(location.getX(), location.getY(), location.getZ());
            
            // Find or create player entity
            Entity playerEntity = findPlayerEntity(entityManager, playerId);
            if (playerEntity == null) {
                playerEntity = new Entity();
                playerEntity.addComponent(new PlayerPositionsComponent(playerId, positions));
                entityManager.addEntity(playerEntity);
            } else {
                // Update player positions
                playerEntity.removeComponent(PlayerPositionsComponent.class);
                playerEntity.addComponent(new PlayerPositionsComponent(playerId, positions));
            }
        }
        
        // Remove offline players from cache
        playerPositionsCache.keySet().removeIf(playerId -> Bukkit.getPlayer(playerId) == null);
    }
    
    private Entity findPlayerEntity(EntityManager entityManager, UUID playerId) {
        for (Entity entity : entityManager.getEntitiesWithComponent(PlayerPositionsComponent.class)) {
            PlayerPositionsComponent component = entity.getComponent(PlayerPositionsComponent.class);
            if (component.playerId().equals(playerId)) {
                return entity;
            }
        }
        return null;
    }
}