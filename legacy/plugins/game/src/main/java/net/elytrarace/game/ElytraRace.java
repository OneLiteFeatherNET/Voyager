package net.elytrarace.game;

import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.common.language.LanguageService;
import net.elytrarace.game.components.GameStateComponent;
import net.elytrarace.game.service.GameService;
import net.elytrarace.game.system.CollisionSystem;
import net.elytrarace.game.system.CupSystem;
import net.elytrarace.game.system.GameStateSystem;
import net.elytrarace.game.system.PlayerUpdateSystem;
import net.elytrarace.game.system.SplineSystem;
import net.elytrarace.game.util.ElytraMarkers;
import net.elytrarace.game.util.PluginInstanceHolder;
import net.elytrarace.game.world.VoidGenProvider;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;

public class ElytraRace extends JavaPlugin {

    private EntityManager entityManager;
    private PlayerUpdateSystem playerUpdateSystem;
    private CupSystem cupSystem;
    private SplineSystem splineSystem;

    @Override
    public void onEnable() {
        try {
            Files.createDirectories(getDataPath());
        } catch (IOException e) {
            getLogger().warning("Unable to create plugin directory");
        }
        PluginInstanceHolder.setPluginInstance(this);

        // Initialize EntityManager
        entityManager = new EntityManager();
        PluginInstanceHolder.setEntityManager(entityManager);

        // Create game state entity
        Entity gameStateEntity = new Entity();
        gameStateEntity.addComponent(GameStateComponent.create());

        // Add the entity to the entity manager
        entityManager.addEntity(gameStateEntity);

        // Register systems
        entityManager.addSystem(new CollisionSystem());
        entityManager.addSystem(new GameStateSystem());

        playerUpdateSystem = new PlayerUpdateSystem();
        entityManager.addSystem(playerUpdateSystem);

        cupSystem = new CupSystem();
        entityManager.addSystem(cupSystem);

        splineSystem = new SplineSystem();
        entityManager.addSystem(splineSystem);

        // Schedule entity update task
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            // Update player positions
            playerUpdateSystem.updateAllPlayers();

            // Update all systems
            entityManager.update(1.0f / 20.0f);
        }, 0L, 1L);

        LanguageService
                .create("elytrarace", Key.key("elytrarace", "language"), getDataFolder().toPath())
                .loadLanguage()
                .thenRun(() -> getLogger().info("Language has been loaded"))
                .join();
        GameService gameService = GameService.create(this);
        gameService.init().thenRun(() -> getLogger().info("Game service has been initialized")).exceptionally(throwable -> {
            getComponentLogger().error(ElytraMarkers.EXCEPTION, "An error occurred while initializing the game service", throwable);
            Bukkit.shutdown();
            return null;
        });
        getLogger().info("ElytraRace has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("ElytraRace has been disabled!");
    }

    @Override
    public @org.jetbrains.annotations.Nullable ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @org.jetbrains.annotations.Nullable String id) {
        return new VoidGenProvider();
    }
}
