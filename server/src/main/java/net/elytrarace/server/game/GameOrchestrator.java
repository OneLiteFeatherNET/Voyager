package net.elytrarace.server.game;

import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.server.cup.CupDefinition;
import net.elytrarace.server.cup.MapDefinition;
import net.elytrarace.server.ecs.GameEntityFactory;
import net.elytrarace.server.ecs.component.ActiveMapComponent;
import net.elytrarace.server.ecs.component.CupProgressComponent;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.ecs.system.ElytraPhysicsSystem;
import net.elytrarace.server.ecs.system.OutOfBoundsSystem;
import net.elytrarace.server.ecs.system.RingCollisionSystem;
import net.elytrarace.server.ecs.system.RingEffectSystem;
import net.elytrarace.server.ecs.system.RingVisualizationSystem;
import net.elytrarace.server.ecs.system.ScoreDisplaySystem;
import net.elytrarace.server.phase.GamePhaseFactory;
import net.elytrarace.server.player.PlayerEventHandler;
import net.elytrarace.server.player.PlayerService;
import net.elytrarace.server.ui.GameHudManager;
import net.elytrarace.server.world.MapInstanceService;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.theevilreaper.xerus.api.phase.LinearPhaseSeries;
import net.theevilreaper.xerus.api.phase.Phase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Connects all game subsystems (ECS, phases, scoring, HUD, map loading) into
 * a single orchestrated game flow.
 * <p>
 * Lifecycle: construct -> {@link #startGame(CupDefinition)} -> {@link #loadNextMap()}
 * (called automatically or externally when a map transition is needed).
 */
public final class GameOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameOrchestrator.class);

    private final PlayerService playerService;
    private final MapInstanceService mapInstanceService;
    private final PlayerEventHandler playerEventHandler;
    private final GameHudManager hudManager;
    private final EntityManager entityManager;

    private Entity gameEntity;
    private LinearPhaseSeries<Phase> phaseSeries;

    public GameOrchestrator(PlayerService playerService, MapInstanceService mapInstanceService,
                            PlayerEventHandler playerEventHandler) {
        this.playerService = Objects.requireNonNull(playerService, "playerService must not be null");
        this.mapInstanceService = Objects.requireNonNull(mapInstanceService, "mapInstanceService must not be null");
        this.playerEventHandler = Objects.requireNonNull(playerEventHandler, "playerEventHandler must not be null");
        this.hudManager = new GameHudManager();
        this.entityManager = new EntityManager();
    }

    /**
     * Starts a new game for the given cup definition.
     * <p>
     * Creates the game entity, registers ECS systems, enrolls all online players,
     * sets up the HUD, and kicks off the phase series (Lobby -> Game -> End).
     *
     * @param cup the cup definition describing the sequence of maps to race
     */
    public void startGame(CupDefinition cup) {
        Objects.requireNonNull(cup, "cup must not be null");
        LOGGER.info("Starting game for cup '{}' with {} maps", cup.name(), cup.maps().size());

        // Create game entity with cup progress and active map tracking
        gameEntity = GameEntityFactory.createGameEntity(cup);
        entityManager.addEntity(gameEntity);

        // Register ECS systems (order matters: physics first, then collision, then bounds)
        entityManager.addSystem(new ElytraPhysicsSystem());
        entityManager.addSystem(new RingCollisionSystem(entityManager, hudManager));
        entityManager.addSystem(new OutOfBoundsSystem(entityManager, playerService));
        entityManager.addSystem(new RingEffectSystem());
        entityManager.addSystem(new RingVisualizationSystem(entityManager));
        entityManager.addSystem(new ScoreDisplaySystem());

        // Create player entities for all currently online players
        for (Player player : playerService.getOnlinePlayers()) {
            Entity playerEntity = GameEntityFactory.createPlayerEntity(player);
            entityManager.addEntity(playerEntity);
            hudManager.addPlayer(player);
        }

        // Create and start the phase series
        // - onMapSwitch: loadNextMap() is triggered when lobby ends
        // - onGamePhaseFinished: advance to next map or let series proceed to end phase
        phaseSeries = GamePhaseFactory.createGamePhases(entityManager,
                () -> loadNextMap().exceptionally(ex -> {
                    LOGGER.error("Failed to load first map after lobby", ex);
                    return null;
                }),
                () -> advanceToNextMap().exceptionally(ex -> {
                    LOGGER.error("Failed to advance to next map", ex);
                    return null;
                }));
        phaseSeries.start();

        LOGGER.info("Game started with {} players", playerService.getOnlinePlayers().size());
    }

    /**
     * Loads the next map from the cup progression.
     * <p>
     * Reads the current map from {@link CupProgressComponent}, loads the world
     * instance via the {@link MapInstanceService}, teleports all online players to
     * the spawn position, and updates the HUD with map name and cup progress.
     *
     * @return a future that completes when the map has been loaded and players teleported
     * @throws IllegalStateException if the cup is already complete or no game is running
     */
    public CompletableFuture<Void> loadNextMap() {
        if (gameEntity == null) {
            throw new IllegalStateException("No game is currently running");
        }

        var cupProgress = gameEntity.getComponent(CupProgressComponent.class);
        MapDefinition mapDef = cupProgress.getCurrentMap();
        if (mapDef == null) {
            throw new IllegalStateException("Cup is already complete, no more maps to load");
        }

        LOGGER.info("Loading map '{}' (map {}/{})", mapDef.name(),
                cupProgress.getCurrentMapIndex() + 1, cupProgress.totalMaps());

        return mapInstanceService.loadMap(mapDef.name(), mapDef.worldDirectory()).thenAccept(instance -> {
            // Update game entity with the loaded map
            var activeMap = gameEntity.getComponent(ActiveMapComponent.class);
            activeMap.setMapInstance(instance);
            activeMap.setCurrentMap(mapDef);

            // Push per-map boost config to the event handler
            playerEventHandler.setBoostConfig(mapDef.boostConfig());

            // Teleport all online players, equip race kit, and activate elytra flight
            Pos spawn = mapDef.spawnPos();
            for (Player player : playerService.getOnlinePlayers()) {
                playerService.teleportToInstance(player, instance, spawn)
                        .thenRun(() -> {
                            playerService.equipForRace(player);
                            activateElytraFlight(player);
                        });
            }

            // Show HUD elements
            hudManager.showMapTitleToAll(mapDef.name());
            hudManager.showCupProgressToAll(
                    cupProgress.getCup().name(),
                    cupProgress.getCurrentMapIndex() + 1,
                    cupProgress.totalMaps()
            );

            LOGGER.info("Map '{}' loaded and players teleported", mapDef.name());
        });
    }

    /**
     * Advances the cup to the next map and loads it.
     *
     * @return a future that completes when the next map has been loaded
     * @throws IllegalStateException if the cup is already complete
     */
    public CompletableFuture<Void> advanceToNextMap() {
        if (gameEntity == null) {
            throw new IllegalStateException("No game is currently running");
        }

        var cupProgress = gameEntity.getComponent(CupProgressComponent.class);
        cupProgress.advance();

        if (cupProgress.isComplete()) {
            LOGGER.info("Cup is complete, no more maps to load");
            return CompletableFuture.completedFuture(null);
        }

        return loadNextMap();
    }

    /**
     * Activates elytra flight for a player by finding their ECS entity and setting
     * the flying flag on their {@link ElytraFlightComponent}.
     *
     * @param player the player whose elytra flight should be activated
     */
    private void activateElytraFlight(Player player) {
        for (Entity entity : entityManager.getEntities()) {
            if (!entity.hasComponent(PlayerRefComponent.class)) {
                continue;
            }
            var ref = entity.getComponent(PlayerRefComponent.class);
            if (ref.getPlayerId().equals(player.getUuid())) {
                entity.getComponent(ElytraFlightComponent.class).setFlying(true);
                LOGGER.debug("Elytra flight activated for player {}", player.getUsername());
                return;
            }
        }
        // Player joined after startGame() — create entity on-demand
        Entity playerEntity = GameEntityFactory.createPlayerEntity(player);
        playerEntity.getComponent(ElytraFlightComponent.class).setFlying(true);
        entityManager.addEntity(playerEntity);
        hudManager.addPlayer(player);
        LOGGER.info("Late-join: created ECS entity and activated elytra flight for player {}", player.getUsername());
    }

    /**
     * Cleans up all resources associated with this game.
     * Should be called when the game ends.
     */
    public void cleanup() {
        hudManager.cleanup();
        LOGGER.info("Game orchestrator cleaned up");
    }

    /**
     * Returns the ECS entity manager driving this game.
     */
    public EntityManager getEntityManager() {
        return entityManager;
    }

    /**
     * Returns the game entity tracking cup progress and active map.
     */
    public Entity getGameEntity() {
        return gameEntity;
    }

    /**
     * Returns the HUD manager for this game.
     */
    public GameHudManager getHudManager() {
        return hudManager;
    }

    /**
     * Returns the current phase series, or {@code null} if no game has been started.
     */
    public LinearPhaseSeries<Phase> getPhaseSeries() {
        return phaseSeries;
    }
}
