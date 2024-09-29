package net.elytrarace.game.service;

import net.elytrarace.api.database.service.DatabaseService;
import net.elytrarace.api.phase.LinearPhaseSeries;
import net.elytrarace.api.phase.Phase;
import net.elytrarace.common.cup.CupService;
import net.elytrarace.common.cup.model.CupDTO;
import net.elytrarace.common.cup.model.ResolvedCupDTO;
import net.elytrarace.common.map.MapService;
import net.elytrarace.game.ElytraRace;
import net.elytrarace.game.listener.DefaultListener;
import net.elytrarace.game.model.GameMapDTO;
import net.elytrarace.game.model.GameSession;
import net.elytrarace.game.phase.EndPhase;
import net.elytrarace.game.phase.GamePhase;
import net.elytrarace.game.phase.LobbyPhase;
import net.elytrarace.game.phase.PreparationPhase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PaperSimpleSenderMapper;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

class GameServiceImpl implements GameService {

    private static final ComponentLogger LOGGER = ComponentLogger.logger(GameServiceImpl.class);

    private volatile ResolvedCupDTO currentCup;
    private LinearPhaseSeries<Phase> elytraPhase = new LinearPhaseSeries<>();
    private final CupService cupService;
    private final MapService mapService;
    private volatile DatabaseService databaseService;
    private PaperCommandManager<Source> commandManager;
    private ElytraRace plugin;
    private volatile GameSession gameSession = new GameSession(UUID.randomUUID(), null, null);

    public GameServiceImpl(ElytraRace plugin) {
        this.plugin = plugin;
        this.cupService = CupService.create(plugin);
        this.mapService = MapService.create(plugin);
        this.elytraPhase.add(new PreparationPhase(this));
        this.elytraPhase.add(new LobbyPhase(this));
        this.elytraPhase.add(new GamePhase(this));
        this.elytraPhase.add(new EndPhase(plugin));
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
                LOGGER.error("An error occurred while initializing the database service", throwable);
                return;
            }
            this.databaseService.init();
        });
        CompletableFuture.runAsync(this::registerCommands);
        CompletableFuture.runAsync(this::registerListeners);
        return this.cupService.getRandomCup().thenCompose(this.mapService::getMapByCup).thenAcceptAsync((cup) -> {
            if (cup == null) {
                LOGGER.error("The map could not be loaded");
                return;
            }
            LOGGER.info("The map has been loaded");
            LOGGER.info("The cup has been loaded");
            LOGGER.info("Setting the current cup to: {}", cup.name());
            if (cup instanceof ResolvedCupDTO resolvedCup) {
                CompletableFuture.completedFuture(resolvedCup).thenCompose( dto -> {
                    var allBukkitMapsLoaded = dto.maps().stream().map(map -> {
                        LOGGER.info("Loaded map: {}", map.name());
                        return CompletableFuture.supplyAsync(() -> WorldCreator.name(map.world())
                                        .generator("VoidGen")
                                        .environment(World.Environment.NORMAL)
                                        .type(WorldType.FLAT)
                                        .createWorld(), runnable -> Bukkit.getScheduler().runTask(getPlugin(), runnable))
                                .thenAccept(world -> {
                                    LOGGER.info("Loaded world: {}", world.getName());
                                }).exceptionally(throwable -> {
                                    LOGGER.error("An error occurred while loading the world", throwable);
                                    return null;
                                });
                    }).toArray(CompletableFuture[]::new);
                    return CompletableFuture.allOf(allBukkitMapsLoaded).thenApply(ignored -> dto);
                }).thenAccept(dto -> {
                    this.currentCup = new ResolvedCupDTO(dto.name(), dto.displayName(), dto.maps().stream().map(GameMapDTO::new).toList());
                    LOGGER.info("The current cup has been set to: {}", this.currentCup.name());
                });


            }


        }).whenCompleteAsync((ignored, ex) -> {
            if (ex != null)
                LOGGER.error("An error occurred while initializing the game service", ex);
        });
    }

    private void registerCommands() {
        // Register commands here
        commandManager.command(commandManager.commandBuilder("voyager")
                .literal("start").flag(commandManager.flagBuilder("speed")).permission("elytrarace.command.start").senderType(PlayerSource.class).handler(context -> {
                var speed = context.flags().hasFlag("speed");
            Player player = context.sender().source();
            player.sendMessage(Component.translatable("phase.lobby.force"));
            Phase currentPhase = this.getElytraPhase().getCurrentPhase();
            if (currentPhase instanceof LobbyPhase lp && !speed) {
                lp.setCurrentTicks(20);
            }
            if (currentPhase instanceof LobbyPhase lp && speed) {
                lp.setCurrentTicks(5);
            }
        }));
    }

    private void registerListeners() {
        // Register listeners here
        Bukkit.getPluginManager().registerEvents(new DefaultListener(this), getPlugin());
    }

    @Override
    public CompletableFuture<Void> switchMap() {
        return CompletableFuture.runAsync(() -> {
            if (this.currentCup == null) {
                LOGGER.error("No cup has been set, shutting down server...");
                Bukkit.shutdown();
            }
            if (this.gameSession.currentMap() == null) {
                this.gameSession = new GameSession(UUID.randomUUID(), this.currentCup, (GameMapDTO) this.currentCup.maps().getFirst());
                return;
            }
            var currentMap = this.gameSession.currentMap();
            var index = this.currentCup.maps().indexOf(currentMap);
            var nextIndex = index + 1;
            if (nextIndex >= this.currentCup.maps().size()) {
                this.gameSession = new GameSession(UUID.randomUUID(), this.currentCup, null);
                return;
            }
            this.gameSession = new GameSession(UUID.randomUUID(), this.currentCup, (GameMapDTO) this.currentCup.maps().get(nextIndex));
        });
    }

    @Override
    public Optional<CupDTO> getCurrentCup() {
        return Optional.ofNullable(this.currentCup);
    }

    @Override
    public synchronized  Optional<GameSession> getGameSession() {
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
    public LinearPhaseSeries<Phase> getElytraPhase() {
        return elytraPhase;
    }

    @Nullable
    public DatabaseService getDatabaseService() {
        return this.databaseService;
    }
}
