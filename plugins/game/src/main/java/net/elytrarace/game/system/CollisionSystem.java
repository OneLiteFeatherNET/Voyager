package net.elytrarace.game.system;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.common.ecs.System;
import net.elytrarace.common.map.model.LocationDTO;
import net.elytrarace.game.components.GameStateComponent;
import net.elytrarace.game.model.GamePortalDTO;
import net.elytrarace.game.player.components.PlayerMetadataComponent;
import net.elytrarace.game.player.components.PlayerPositionsComponent;
import net.elytrarace.game.portal.components.PortalPositionsComponent;
import net.elytrarace.game.util.PluginInstanceHolder;
import org.apache.commons.geometry.euclidean.threed.Vector3D;
import org.apache.commons.geometry.euclidean.threed.line.Lines3D;
import org.apache.commons.numbers.core.Precision;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * System that detects collisions between players and portals.
 */
public class CollisionSystem implements System {

    public static final Precision.DoubleEquivalence PRECISION = Precision.doubleEquivalenceOfEpsilon(1e-6);

    @Override
    public Set<Class<? extends Component>> getRequiredComponents() {
        return Set.of(PlayerPositionsComponent.class);
    }

    @Override
    public void process(Entity entity, float deltaTime) {
        PlayerPositionsComponent positionsComponent = entity.getComponent(PlayerPositionsComponent.class);
        Vector3D[] lastPositions = positionsComponent.lastPositions();
        UUID playerId = positionsComponent.playerId();

        // Skip if we don't have enough positions
        if (lastPositions == null || lastPositions.length < 3) {
            return;
        }

        Vector3D position = lastPositions[0];
        Vector3D position2 = lastPositions[1];
        Vector3D position3 = lastPositions[2];

        // Skip if any position is null or zero
        if (position == null || position2 == null || position3 == null) {
            return;
        }

        if (position.isZero(PRECISION) || position2.isZero(PRECISION) || position3.isZero(PRECISION)) {
            return;
        }

        // Skip if positions are too close
        if (position.vectorTo(position2).isZero(PRECISION) || 
            position2.vectorTo(position3).isZero(PRECISION) || 
            position.vectorTo(position3).isZero(PRECISION)) {
            return;
        }

        // Get the game state entity
        EntityManager entityManager = PluginInstanceHolder.getEntityManager();
        Set<Entity> gameStateEntities = entityManager.getEntitiesWithComponent(GameStateComponent.class);
        if (gameStateEntities.isEmpty()) {
            return;
        }
        Entity gameStateEntity = gameStateEntities.iterator().next();

        GameStateComponent gameStateComponent = gameStateEntity.getComponent(GameStateComponent.class);
        if (gameStateComponent.getCurrentMap().isEmpty()) {
            return;
        }

        var map = gameStateComponent.getCurrentMap().get();
        var portals = map.portals().stream()
                .map(GamePortalDTO.class::cast)
                .collect(Collectors.toCollection(TreeSet::new));

        if (portals.isEmpty()) {
            return;
        }

        // Get the player's last portal
        final int lastPortalIndex;
        if (entity.hasComponent(PlayerMetadataComponent.class)) {
            PlayerMetadataComponent metadataComponent = entity.getComponent(PlayerMetadataComponent.class);
            lastPortalIndex = metadataComponent.lastPortalIndex();
        } else {
            lastPortalIndex = -1;
        }

        // Determine which portal to check
        GamePortalDTO portal;
        if (lastPortalIndex >= 0) {
            final int portalIndex = lastPortalIndex; // Create a final copy for the lambda
            portal = portals.higher(portals.stream()
                    .filter(p -> p.index() == portalIndex)
                    .findFirst()
                    .orElse(portals.first()));
        } else {
            portal = portals.first();
        }

        if (portal == null) {
            return;
        }

        // Check for intersection with the portal
        var firstLine = Lines3D.fromPoints(position, position2, PRECISION);
        var secondLine = Lines3D.fromPoints(position2, position3, PRECISION);
        var thirdLine = Lines3D.fromPoints(position, position3, PRECISION);

        var firstSegment = Lines3D.segmentFromPoints(position, position2, PRECISION);
        var secondSegment = Lines3D.segmentFromPoints(position2, position3, PRECISION);
        var thirdSegment = Lines3D.segmentFromPoints(position, position3, PRECISION);

        var firstIntersection = portal.plane().intersection(firstLine);
        var secondIntersection = portal.plane().intersection(secondLine);
        var thirdIntersection = portal.plane().intersection(thirdLine);

        var firstLineSuccess = firstIntersection != null && 
                portal.bounds().contains(firstIntersection, PRECISION) && 
                firstSegment.contains(firstIntersection) && 
                portal.regionBSPTree3D().contains(firstIntersection);

        var secondLineSuccess = secondIntersection != null && 
                portal.bounds().contains(secondIntersection, PRECISION) && 
                secondSegment.contains(secondIntersection) && 
                portal.regionBSPTree3D().contains(secondIntersection);

        var thirdLineSuccess = thirdIntersection != null && 
                portal.bounds().contains(thirdIntersection, PRECISION) && 
                thirdSegment.contains(thirdIntersection) && 
                portal.regionBSPTree3D().contains(thirdIntersection);

        if (firstLineSuccess || secondLineSuccess || thirdLineSuccess) {
            // Update the player's last portal
            PlayerMetadataComponent metadataComponent = entity.hasComponent(PlayerMetadataComponent.class) ?
                    entity.getComponent(PlayerMetadataComponent.class) :
                    PlayerMetadataComponent.create(playerId);

            PlayerMetadataComponent updatedMetadataComponent = metadataComponent.withLastPortalIndex(portal.index());
            entity.removeComponent(PlayerMetadataComponent.class);
            entity.addComponent(updatedMetadataComponent);

            // Notify the player
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage("You have entered portal: " + portal.index());
            }
        }
    }
}
