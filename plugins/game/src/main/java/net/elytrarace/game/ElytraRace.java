package net.elytrarace.game;

import net.elytrarace.api.database.service.DatabaseService;
import net.elytrarace.api.phase.LinearPhaseSeries;
import net.elytrarace.api.phase.Phase;
import net.elytrarace.common.cup.CupService;
import net.elytrarace.common.cup.model.CupDTO;
import net.elytrarace.common.map.MapService;
import net.elytrarace.game.listener.DefaultListener;
import net.elytrarace.game.phase.EndPhase;
import net.elytrarace.game.phase.GamePhase;
import net.elytrarace.game.phase.LobbyPhase;
import net.elytrarace.game.phase.PreparationPhase;
import net.elytrarace.game.world.VoidGenProvider;
import org.apache.maven.model.building.Result;
import org.bukkit.Bukkit;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class ElytraRace extends JavaPlugin {

    private DatabaseService databaseService;
    private CupService cupService;
    private MapService mapService;
    private LinearPhaseSeries<Phase> elytraPhase;
    private CupDTO currentCup;

    @Override
    public void onEnable() {
        try {
            Files.createDirectories(getDataPath());
        } catch (IOException e) {
            getLogger().warning("Unable to create plugin directory");
        }
        this.elytraPhase = new LinearPhaseSeries<>();
        this.elytraPhase.add(new PreparationPhase(this));
        this.elytraPhase.add(new LobbyPhase(this));
        this.elytraPhase.add(new GamePhase(this));
        this.elytraPhase.add(new EndPhase(this));
        this.elytraPhase.start();
        getLogger().info("ElytraRace has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("ElytraRace has been disabled!");
    }

    private void registerCommands() {
        // Register commands here
    }

    private void registerListeners() {
        // Register listeners here
        Bukkit.getPluginManager().registerEvents(new DefaultListener(this), this);
    }
    private void createDatabaseService() {
        this.databaseService = DatabaseService.create(getDataFolder().toPath());
    }

    public void init() {
        CompletableFuture.runAsync(this::createDatabaseService).whenCompleteAsync((unused, throwable) -> {
            if (throwable != null) {
                getLogger().log(Level.SEVERE, "An error occurred while initializing the database service", throwable);
                return;
            }
            this.databaseService.init();
        });
        this.cupService = CupService.create(this);
        this.mapService = MapService.create(this);
        this.cupService.getRandomCup()
                .thenCompose(this.mapService::getMapByCup)
                .thenAcceptAsync((cup) -> {
                    if (cup == null) {
                        getLogger().severe("The map could not be loaded");
                        return;
                    }
                    getLogger().info("The map has been loaded");
                    getLogger().info("The cup has been loaded");
                    getComponentLogger().info("Setting the current cup to: {}", cup.name());
                    this.currentCup = cup;
                }, runnable -> Bukkit.getScheduler().runTask(this, runnable))
                .whenCompleteAsync((ignored, ex) -> {
                    if (ex != null)
                        getLogger().log(Level.SEVERE, "An error occurred while initializing the plugin", ex);
                }, runnable -> Bukkit.getScheduler().runTask(this, runnable));
        CompletableFuture.runAsync(this::registerCommands);
        CompletableFuture.runAsync(this::registerListeners);
    }

    @Nullable
    public DatabaseService getDatabaseService() {
        return this.databaseService;
    }

    @Nullable
    public CupService getCupService() {
        return this.cupService;
    }

    @Nullable
    public MapService getMapService() {
        return this.mapService;
    }

    @Nullable
    public LinearPhaseSeries<Phase> getElytraPhase() {
        return this.elytraPhase;
    }

    public Optional<CupDTO> getCurrentCup() {
        return Optional.ofNullable(this.currentCup);
    }

    @Override
    public @org.jetbrains.annotations.Nullable ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @org.jetbrains.annotations.Nullable String id) {
        return new VoidGenProvider();
    }
}
