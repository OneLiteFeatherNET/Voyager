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
import org.bukkit.entity.Player;
import org.incendo.cloud.context.CommandContext;
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
    private volatile static boolean DEV_MODE = Boolean.parseBoolean(System.getProperty("ELYTRACERACE_DEV", "true"));

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
        return this.cupService.getRandomCup().thenCompose(this.mapService::getMapByCup)
                .thenCompose(GameCupService::startLoadingWorldAsync)
                .thenAcceptAsync(this::setCurrentCupAsync, Bukkit.getScheduler().getMainThreadExecutor(plugin)).exceptionally((ex) -> {
            LOGGER.error(ElytraMarkers.EXCEPTION, "An error occurred while initializing the game service", ex);
            return null;
        });
    }

    private synchronized void setCurrentCupAsync(ResolvedCupDTO dto) {
        var resolvedCup = new ResolvedCupDTO(dto.name(), dto.displayName(), dto.maps().stream().map(GameMapDTO::fromMapDTO).toList());
        this.gameSession = GameSession.fromWithCurrentCup(this.gameSession, resolvedCup);
        LOGGER.info(ElytraMarkers.CUP, "The current cup has been set to: {}", this.gameSession.currentCup().name());
    }

    private void registerCommands() {
        // Register commands here
        commandManager.command(commandManager.commandBuilder("voyager").literal("start").permission("elytrarace.command.start").senderType(PlayerSource.class).handler(this::handleForceStart));
    }

    private void handleForceStart(CommandContext<PlayerSource> context) {
        Player player = context.sender().source();
        Phase currentPhase = this.getElytraPhase().getCurrentPhase();
        var newTime = DEV_MODE ? 5 : 20;
        if (currentPhase instanceof LobbyPhase lp) {
            lp.setCurrentTicks(newTime);
            player.sendMessage(Component.translatable("phase.lobby.force",Component.translatable("plugin.prefix"), Component.text(newTime)));
        }
    }

    private void registerListeners() {
        // Register listeners here
        Bukkit.getPluginManager().registerEvents(new DefaultListener(this), getPlugin());
    }

    @Override
    public CompletableFuture<GameSession> switchMap() {
        return CompletableFuture.completedFuture(this.gameSession).thenApplyAsync(GameCupService::switchMapInternal).thenApply(this::updateGameSession);
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
    public LinearPhaseSeries<Phase> getElytraPhase() {
        return elytraPhase;
    }

    @Nullable
    public DatabaseService getDatabaseService() {
        return this.databaseService;
    }

    @Override
    public void onUpdate() {
        PortalDetectionService.handlePortalDetection(this);
    }
}
