package net.elytrarace.server.ecs;

import net.elytrarace.common.ecs.Entity;
import net.elytrarace.server.cup.CupDefinition;
import net.elytrarace.server.ecs.component.ActiveMapComponent;
import net.elytrarace.server.ecs.component.CupProgressComponent;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.ecs.component.RingTrackerComponent;
import net.elytrarace.server.ecs.component.ScoreComponent;
import net.minestom.server.entity.Player;

/**
 * Factory for creating pre-configured ECS entities used during gameplay.
 */
public final class GameEntityFactory {

    private GameEntityFactory() {
        // utility class
    }

    /**
     * Creates a player entity with all components needed for elytra racing gameplay.
     *
     * @param player the Minestom player instance
     * @return a fully configured player entity
     */
    public static Entity createPlayerEntity(Player player) {
        Entity entity = new Entity();
        entity.addComponent(new PlayerRefComponent(player.getUuid(), player));
        entity.addComponent(new ElytraFlightComponent());
        entity.addComponent(new RingTrackerComponent());
        entity.addComponent(new ScoreComponent());
        return entity;
    }

    /**
     * Creates a game entity that tracks cup progress and the active map.
     *
     * @param cup the cup definition describing the sequence of maps
     * @return a fully configured game entity
     */
    public static Entity createGameEntity(CupDefinition cup) {
        Entity entity = new Entity();
        entity.addComponent(new CupProgressComponent(cup));
        entity.addComponent(new ActiveMapComponent());
        return entity;
    }
}
