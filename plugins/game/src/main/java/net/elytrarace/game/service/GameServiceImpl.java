package net.elytrarace.game.service;

import net.elytrarace.api.database.service.DatabaseService;
import net.elytrarace.api.phase.EventRegistrar;
import net.elytrarace.api.phase.LinearPhaseSeries;
import net.elytrarace.api.phase.Phase;
import net.elytrarace.api.phase.PhaseScheduler;
import net.elytrarace.common.cup.CupService;
import net.elytrarace.game.platform.BukkitEventRegistrar;
import net.elytrarace.game.platform.BukkitPhaseScheduler;
import net.elytrarace.common.cup.model.CupDTO;
import net.elytrarace.common.cup.model.ResolvedCupDTO;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.common.map.MapService;
import net.elytrarace.game.ElytraRace;
import net.elytrarace.game.components.CupComponent;
import net.elytrarace.game.components.GameStateComponent;
import net.elytrarace.game.listener.DefaultListener;
import net.elytrarace.game.model.GameMapDTO;
import net.elytrarace.game.model.GameSession;
import net.elytrarace.game.phase.EndPhase;
import net.elytrarace.game.phase.GamePhase;
import net.elytrarace.game.phase.LobbyPhase;
import net.elytrarace.game.phase.PreparationPhase;
import net.elytrarace.game.system.CupSystem;
import net.elytrarace.game.system.GameStateSystem;
import net.elytrarace.game.system.PhaseSystem;
import net.elytrarace.game.util.ElytraMarkers;
import net.elytrarace.game.util.PluginInstanceHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PaperSimpleSenderMapper;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

class GameServiceImpl implements GameService {

    private static final ComponentLogger LOGGER = ComponentLogger.logger(GameServiceImpl.class);
    private volatile static boolean DEV_MODE = Boolean.parseBoolean(System.getProperty("ELYTRACERACE_DEV", "true"));

    private LinearPhaseSeries<Phase> elytraPhase = new LinearPhaseSeries<>();
    private final CupService cupService;
    private final MapService mapService;
    private volatile DatabaseService databaseService;
    private PaperCommandManager<Source> commandManager;
    private ElytraRace plugin;
    private final PhaseScheduler phaseScheduler;
    private final EventRegistrar eventRegistrar;
    private volatile GameSession gameSession = new GameSession(UUID.randomUUID(), null, null);

    public GameServiceImpl(ElytraRace plugin) {
        this.plugin = plugin;
        this.phaseScheduler = new BukkitPhaseScheduler(plugin);
        this.eventRegistrar = new BukkitEventRegistrar(plugin);
        this.cupService = CupService.create(plugin.getDataPath());
        this.mapService = MapService.create(plugin.getDataPath());
        this.elytraPhase.add(new PreparationPhase(this));
        this.elytraPhase.add(new LobbyPhase(this));
        this.elytraPhase.add(new GamePhase(this));
        this.elytraPhase.add(new EndPhase(phaseScheduler, eventRegistrar));
        this.elytraPhase.start();
        this.commandManager = PaperCommandManager.builder(PaperSimpleSenderMapper.simpleSenderMapper()).executionCoordinator(ExecutionCoordinator.asyncCoordinator()).buildOnEnable(getPlugin());
    }

    private void createDatabaseService() {
        this.databaseService = DatabaseService.create(getPlugin().getDataFolder().toPath());
    }

    @Override
    public CompletableFuture<Void> init() {
        CompletableFuture.runAsync(this::createDatabaseService).whenCompleteAsync((unused, throwable) -> {
            if (throwable != null) {
                LOGGER.error(ElytraMarkers.EXCEPTION, "An error occurred while initializing the database service", throwable);
                Bukkit.shutdown();
                return;
            }
            this.databaseService.init();
        }).exceptionally(throwable -> {
            LOGGER.error(ElytraMarkers.EXCEPTION, "An error occurred while initializing the database service", throwable);
            Bukkit.shutdown();
            return null;
        });
        CompletableFuture.runAsync(this::registerCommands);
        CompletableFuture.runAsync(this::registerListeners);

        // Get the entity manager
        EntityManager entityManager = PluginInstanceHolder.getEntityManager();

        // Find the CupSystem
        CupSystem cupSystem = null;
        for (net.elytrarace.common.ecs.System system : entityManager.getSystems()) {
            if (system instanceof CupSystem) {
                cupSystem = (CupSystem) system;
                break;
            }
        }

        // If we couldn't find the CupSystem, log an error and continue with the old approach
        if (cupSystem == null) {
            LOGGER.error(ElytraMarkers.ECS, "Could not find CupSystem, falling back to old approach");
            return this.cupService.getRandomCup().thenCompose(this.mapService::getMapByCup)
                    .thenCompose(GameCupService::startLoadingWorldAsync)
                    .thenAcceptAsync(this::setCurrentCupAsync, Bukkit.getScheduler().getMainThreadExecutor(plugin)).exceptionally((ex) -> {
                LOGGER.error(ElytraMarkers.EXCEPTION, "An error occurred while initializing the game service", ex);
                return null;
            });
        }

        // Find the game state entity
        Set<Entity> gameStateEntities = entityManager.getEntitiesWithComponent(GameStateComponent.class);
        if (gameStateEntities.isEmpty()) {
            LOGGER.error(ElytraMarkers.ECS, "No game state entity found, falling back to old approach");
            return this.cupService.getRandomCup().thenCompose(this.mapService::getMapByCup)
                    .thenCompose(GameCupService::startLoadingWorldAsync)
                    .thenAcceptAsync(this::setCurrentCupAsync, Bukkit.getScheduler().getMainThreadExecutor(plugin)).exceptionally((ex) -> {
                LOGGER.error(ElytraMarkers.EXCEPTION, "An error occurred while initializing the game service", ex);
                return null;
            });
        }

        Entity gameStateEntity = gameStateEntities.iterator().next();

        // Use the ECS approach
        final CupSystem finalCupSystem = cupSystem;
        final Entity finalGameStateEntity = gameStateEntity;

        return this.cupService.getRandomCup().thenCompose(this.mapService::getMapByCup)
                .thenApply(cup -> {
                    if (cup instanceof ResolvedCupDTO resolvedCup) {
                        // Create a CupComponent and add it to the game state entity
                        CupComponent cupComponent = CupComponent.create(resolvedCup);
                        finalGameStateEntity.addComponent(cupComponent);

                        // Load the worlds using the CupSystem
                        finalCupSystem.loadWorlds(finalGameStateEntity).join();

                        // Update the game state
                        GameStateComponent gameStateComponent = finalGameStateEntity.getComponent(GameStateComponent.class);
                        GameStateComponent updatedComponent = gameStateComponent.withCurrentCup(resolvedCup);
                        finalGameStateEntity.removeComponent(GameStateComponent.class);
                        finalGameStateEntity.addComponent(updatedComponent);

                        // Update our local game session
                        this.gameSession = updatedComponent.gameSession();

                        return resolvedCup;
                    }
                    return null;
                })
                .thenAcceptAsync(cup -> {
                    if (cup != null) {
                        LOGGER.info(ElytraMarkers.CUP, "The current cup has been set to: {}", cup.name());
                    }
                }, Bukkit.getScheduler().getMainThreadExecutor(plugin))
                .exceptionally((ex) -> {
                    LOGGER.error(ElytraMarkers.EXCEPTION, "An error occurred while initializing the game service", ex);
                    return null;
                });
    }

    private synchronized void setCurrentCupAsync(ResolvedCupDTO dto) {
        var resolvedCup = new ResolvedCupDTO(dto.name(), dto.displayName(), dto.maps().stream().map(GameMapDTO::fromMapDTO).toList());

        // Update game session in this service
        this.gameSession = GameSession.fromWithCurrentCup(this.gameSession, resolvedCup);

        // Update game state in ECS
        updateGameStateInECS(this.gameSession);

        LOGGER.info(ElytraMarkers.CUP, "The current cup has been set to: {}", this.gameSession.currentCup().name());
    }

    private void updateGameStateInECS(GameSession gameSession) {
        // Find the GameStateSystem
        var entityManager = PluginInstanceHolder.getEntityManager();
        var gameStateEntities = entityManager.getEntitiesWithComponent(GameStateComponent.class);
        if (gameStateEntities.isEmpty()) {
            LOGGER.error(ElytraMarkers.ECS, "No game state entity found");
            return;
        }

        // Get the first game state entity
        var gameStateEntity = gameStateEntities.iterator().next();

        // Update the game state component
        var gameStateComponent = gameStateEntity.getComponent(GameStateComponent.class);
        var updatedComponent = gameStateComponent.withGameSession(gameSession);
        gameStateEntity.removeComponent(GameStateComponent.class);
        gameStateEntity.addComponent(updatedComponent);
    }

    private void registerCommands() {
        // Register commands here
        commandManager.command(commandManager.commandBuilder("voyager").literal("start").permission("elytrarace.command.start").senderType(PlayerSource.class).handler(this::handleForceStart));
    }

    private void handleForceStart(CommandContext<PlayerSource> context) {
        Player player = context.sender().source();
        var newTime = DEV_MODE ? 5 : 20;

        // Get the entity manager
        EntityManager entityManager = PluginInstanceHolder.getEntityManager();

        // Find the PhaseSystem
        PhaseSystem phaseSystem = null;
        for (net.elytrarace.common.ecs.System system : entityManager.getSystems()) {
            if (system instanceof PhaseSystem) {
                phaseSystem = (PhaseSystem) system;
                break;
            }
        }

        // If we couldn't find the PhaseSystem, log an error and fall back to the old approach
        if (phaseSystem == null) {
            LOGGER.error(ElytraMarkers.ECS, "No PhaseSystem found, falling back to old approach");
            Phase currentPhase = this.getElytraPhase().getCurrentPhase();
            if (currentPhase instanceof LobbyPhase lp) {
                lp.setCurrentTicks(newTime);
                player.sendMessage(Component.translatable("phase.lobby.force", Component.translatable("plugin.prefix"), Component.text(newTime)));
            }
            return;
        }

        // Find the game state entity
        var gameStateEntities = entityManager.getEntitiesWithComponent(GameStateComponent.class);
        if (gameStateEntities.isEmpty()) {
            LOGGER.error(ElytraMarkers.ECS, "No game state entity found, falling back to old approach");
            Phase currentPhase = this.getElytraPhase().getCurrentPhase();
            if (currentPhase instanceof LobbyPhase lp) {
                lp.setCurrentTicks(newTime);
                player.sendMessage(Component.translatable("phase.lobby.force", Component.translatable("plugin.prefix"), Component.text(newTime)));
            }
            return;
        }

        Entity gameStateEntity = gameStateEntities.iterator().next();

        // Use the PhaseSystem to force start
        boolean success = phaseSystem.forceStart(gameStateEntity, player, newTime);

        // If the PhaseSystem couldn't force start, fall back to the old approach
        if (!success) {
            LOGGER.error(ElytraMarkers.ECS, "PhaseSystem couldn't force start, falling back to old approach");
            Phase currentPhase = this.getElytraPhase().getCurrentPhase();
            if (currentPhase instanceof LobbyPhase lp) {
                lp.setCurrentTicks(newTime);
                player.sendMessage(Component.translatable("phase.lobby.force", Component.translatable("plugin.prefix"), Component.text(newTime)));
            }
        }
    }

    private void registerListeners() {
        // Register listeners here
        Bukkit.getPluginManager().registerEvents(new DefaultListener(this), getPlugin());
    }

    @Override
    public CompletableFuture<GameSession> switchMap() {
        // Get the entity manager
        EntityManager entityManager = PluginInstanceHolder.getEntityManager();

        // Find the CupSystem
        CupSystem cupSystem = null;
        for (net.elytrarace.common.ecs.System system : entityManager.getSystems()) {
            if (system instanceof CupSystem) {
                cupSystem = (CupSystem) system;
                break;
            }
        }

        // If we couldn't find the CupSystem, log an error and fall back to the old approach
        if (cupSystem == null) {
            LOGGER.error(ElytraMarkers.ECS, "No CupSystem found, falling back to GameStateSystem");

            // Get the game state entity and system
            var gameStateEntities = entityManager.getEntitiesWithComponent(GameStateComponent.class);
            if (gameStateEntities.isEmpty()) {
                LOGGER.error(ElytraMarkers.ECS, "No game state entity found");
                return CompletableFuture.completedFuture(this.gameSession);
            }

            Entity gameStateEntity = gameStateEntities.iterator().next();

            // Find the GameStateSystem
            var systems = entityManager.getSystems();
            var gameStateSystem = systems.stream()
                    .filter(system -> system instanceof GameStateSystem)
                    .map(system -> (GameStateSystem) system)
                    .findFirst()
                    .orElse(null);

            if (gameStateSystem == null) {
                LOGGER.error(ElytraMarkers.ECS, "No GameStateSystem found");
                return CompletableFuture.completedFuture(this.gameSession);
            }

            // Use the GameStateSystem to switch the map
            return gameStateSystem.switchMap(gameStateEntity)
                    .thenApply(this::updateGameSession);
        }

        // Find the game state entity
        var gameStateEntities = entityManager.getEntitiesWithComponent(GameStateComponent.class);
        if (gameStateEntities.isEmpty()) {
            LOGGER.error(ElytraMarkers.ECS, "No game state entity found");
            return CompletableFuture.completedFuture(this.gameSession);
        }

        Entity gameStateEntity = gameStateEntities.iterator().next();

        // Use the CupSystem to switch the map
        return cupSystem.switchMap(gameStateEntity)
                .thenApply(this::updateGameSession);
    }

    private synchronized GameSession updateGameSession(GameSession gameSession) {
        this.gameSession = gameSession;
        return gameSession;
    }

    @Override
    public Optional<CupDTO> getCurrentCup() {
        return getGameSession().map(GameSession::currentCup);
    }

    @Override
    public synchronized Optional<GameSession> getGameSession() {
        return Optional.ofNullable(this.gameSession);
    }

    @Override
    public synchronized Optional<GameMapDTO> getCurrentMap() {
        return this.getGameSession().map(GameSession::currentMap);
    }

    @Override
    public ElytraRace getPlugin() {
        return plugin;
    }

    @Override
    public PhaseScheduler getPhaseScheduler() {
        return phaseScheduler;
    }

    @Override
    public EventRegistrar getEventRegistrar() {
        return eventRegistrar;
    }

    @Override
    public LinearPhaseSeries<Phase> getElytraPhase() {
        return elytraPhase;
    }

    @Nullable
    public DatabaseService getDatabaseService() {
        return this.databaseService;
    }

    @Override
    public void onUpdate() {
        // Portal detection is now handled by the CollisionSystem in the ECS architecture
    }
}
