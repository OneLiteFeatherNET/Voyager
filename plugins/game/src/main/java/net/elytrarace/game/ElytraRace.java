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
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nullable;
import java.util.Optional;

public class ElytraRace extends JavaPlugin {

    private DatabaseService databaseService;
    private CupService cupService;
    private MapService mapService;
    private LinearPhaseSeries<Phase> elytraPhase;
    private CupDTO currentCup;

    @Override
    public void onEnable() {
        this.databaseService = DatabaseService.create(getDataFolder().toPath());
        this.databaseService.init();
        this.cupService = CupService.create(this);
        this.mapService = MapService.create(this);
        this.cupService.getRandomCup()
                .thenComposeAsync(this.mapService::getMapByCup)
                .thenAcceptAsync(cup -> {
            this.currentCup = cup;
        }, Bukkit.getScheduler().getMainThreadExecutor(this));
        this.elytraPhase = new LinearPhaseSeries<>();
        this.elytraPhase.add(new LobbyPhase(this));
        this.elytraPhase.add(new GamePhase(this));
        this.elytraPhase.add(new EndPhase(this));
        this.elytraPhase.start();
        registerCommands();
        registerListeners();
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
}
