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
import net.elytrarace.game.util.ElytraMarkers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PaperSimpleSenderMapper;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
        return this.cupService.getRandomCup().thenCompose(this.mapService::getMapByCup).thenAcceptAsync(this::startLoadingWorldAsync).exceptionally((ex) -> {
            LOGGER.error(ElytraMarkers.EXCEPTION, "An error occurred while initializing the game service", ex);
            return null;
        });
    }

    private void startLoadingWorldAsync(CupDTO cup) {
        if (cup == null) {
            LOGGER.error("The map could not be loaded");
            return;
        }
        LOGGER.info("The map has been loaded");
        LOGGER.info("The cup has been loaded");
        LOGGER.info("Setting the current cup to: {}", cup.name());
        if (cup instanceof ResolvedCupDTO resolvedCup) {
            CompletableFuture.completedFuture(resolvedCup).thenCompose(this::loadMaps).thenAccept(this::setCurrentCupAsync);
        }
    }

    private void setCurrentCupAsync(ResolvedCupDTO dto) {
        this.currentCup = new ResolvedCupDTO(dto.name(), dto.displayName(), dto.maps().stream().map(GameMapDTO::new).toList());
        LOGGER.info("The current cup has been set to: {}", this.currentCup.name());
    }

    private @NotNull CompletionStage<ResolvedCupDTO> loadMaps(ResolvedCupDTO dto) {
        var allBukkitMapsLoaded = dto.maps().stream().map(map -> {
            LOGGER.info("Loaded map: {}", map.name());
            return CompletableFuture.completedFuture(map).thenApplyAsync(mapDTO -> WorldCreator.name(mapDTO.world()).generator("ElytraRace").environment(World.Environment.NORMAL).type(WorldType.FLAT).createWorld(), Bukkit.getScheduler().getMainThreadExecutor(getPlugin())).thenAccept(world -> LOGGER.info("Loaded world: {}", world.getName())).exceptionally(throwable -> {
                LOGGER.error(ElytraMarkers.EXCEPTION, "An error occurred while loading the world", throwable);
                return null;
            });
        }).toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(allBukkitMapsLoaded).thenApply(ignored -> dto);
    }

    private void registerCommands() {
        // Register commands here
        commandManager.command(commandManager.commandBuilder("voyager").literal("start").flag(commandManager.flagBuilder("speed")).permission("elytrarace.command.start").senderType(PlayerSource.class).handler(this::handleForceStart));
    }

    private void handleForceStart(CommandContext<PlayerSource> context) {
        var speed = context.flags().hasFlag("speed");
        Player player = context.sender().source();
        Phase currentPhase = this.getElytraPhase().getCurrentPhase();
        if (currentPhase instanceof LobbyPhase lp && !speed) {
            lp.setCurrentTicks(20);
            player.sendMessage(Component.translatable("phase.lobby.force",Component.translatable("plugin.prefix"), Component.text("20")));
        }
        if (currentPhase instanceof LobbyPhase lp && speed) {
            player.sendMessage(Component.translatable("phase.lobby.force",Component.translatable("plugin.prefix"), Component.text("5")));
            lp.setCurrentTicks(5);
        }
    }

    private void registerListeners() {
        // Register listeners here
        Bukkit.getPluginManager().registerEvents(new DefaultListener(this), getPlugin());
    }

    @Override
    public CompletableFuture<GameSession> switchMap() {
        return CompletableFuture.supplyAsync(this::switchMapInternal);
    }

    private GameSession switchMapInternal() {
        if (this.currentCup == null) {
            LOGGER.error("No cup has been set, shutting down server...");
            Bukkit.shutdown();
        }
        if (this.gameSession.currentMap() == null) {
            this.gameSession = new GameSession(UUID.randomUUID(), this.currentCup, (GameMapDTO) this.currentCup.maps().getFirst());
            return this.gameSession;
        }
        var currentMap = this.gameSession.currentMap();
        var index = this.currentCup.maps().indexOf(currentMap);
        var nextIndex = index + 1;
        if (nextIndex >= this.currentCup.maps().size()) {
            this.gameSession = new GameSession(UUID.randomUUID(), this.currentCup, null);
            return this.gameSession;
        }
        this.gameSession = new GameSession(UUID.randomUUID(), this.currentCup, (GameMapDTO) this.currentCup.maps().get(nextIndex));
        return this.gameSession;
    }

    @Override
    public Optional<CupDTO> getCurrentCup() {
        return Optional.ofNullable(this.currentCup);
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
    public LinearPhaseSeries<Phase> getElytraPhase() {
        return elytraPhase;
    }

    @Nullable
    public DatabaseService getDatabaseService() {
        return this.databaseService;
    }
}
