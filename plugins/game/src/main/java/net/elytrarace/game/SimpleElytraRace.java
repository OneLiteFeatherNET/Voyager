package net.elytrarace.game;

import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.common.language.LanguageService;
import net.elytrarace.game.components.CurrentCupComponent;
import net.elytrarace.game.components.CurrentMapComponent;
import net.elytrarace.game.components.SessionComponent;
import net.elytrarace.game.components.SimplePhaseComponent;
import net.elytrarace.game.system.SimpleGameStateSystem;
import net.elytrarace.game.system.SimpleCupSystem;
import net.elytrarace.game.system.SimplePhaseSystem;
import net.elytrarace.game.system.SimpleSplineSystem;
import net.elytrarace.game.system.PlayerUpdateSystem;
import net.elytrarace.game.system.CollisionSystem;
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

/**
 * Main plugin class using the flattened ECS architecture.
 */
public class SimpleElytraRace extends JavaPlugin {

    private EntityManager entityManager;
    private PlayerUpdateSystem playerUpdateSystem;
    private SimplePhaseSystem phaseSystem;
    private SimpleCupSystem cupSystem;
    private SimpleSplineSystem splineSystem;
    private SimpleGameStateSystem gameStateSystem;

    @Override
    public void onEnable() {
        try {
            Files.createDirectories(getDataFolder().toPath());
        } catch (IOException e) {
            getLogger().warning("Unable to create plugin directory");
        }
        PluginInstanceHolder.setPluginInstance(this);

        // Initialize EntityManager
        entityManager = new EntityManager();
        PluginInstanceHolder.setEntityManager(entityManager);

        // Create game state entity
        Entity gameStateEntity = new Entity();
        gameStateEntity.addComponent(SessionComponent.create());
        gameStateEntity.addComponent(CurrentCupComponent.createEmpty());
        gameStateEntity.addComponent(CurrentMapComponent.createEmpty());
        gameStateEntity.addComponent(SimplePhaseComponent.createPreparation());

        // Add the entity to the entity manager
        entityManager.addEntity(gameStateEntity);

        // Register systems
        gameStateSystem = new SimpleGameStateSystem();
        entityManager.addSystem(gameStateSystem);

        playerUpdateSystem = new PlayerUpdateSystem();
        entityManager.addSystem(playerUpdateSystem);

        phaseSystem = new SimplePhaseSystem();
        entityManager.addSystem(phaseSystem);

        cupSystem = new SimpleCupSystem();
        entityManager.addSystem(cupSystem);

        splineSystem = new SimpleSplineSystem();
        entityManager.addSystem(splineSystem);

        entityManager.addSystem(new CollisionSystem());

        // Schedule entity update task
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            // Update player positions
            playerUpdateSystem.updateAllPlayers();

            // Update all systems
            entityManager.update(1.0f / 20.0f);
        }, 0L, 1L);

        // Load language
        LanguageService
                .create("elytrarace", Key.key("elytrarace", "language"), this)
                .loadLanguage()
                .thenRun(() -> getLogger().info("Language has been loaded"));

        // Initialize the game
        initializeGame(gameStateEntity);

        getLogger().info("SimpleElytraRace has been enabled!");
    }

    /**
     * Initializes the game by loading a random cup and setting it as the current cup.
     */
    private void initializeGame(Entity gameStateEntity) {
        // In a real implementation, this would load a cup from a service
        // For now, we'll create a simple cup with a single map
        Key cupId = Key.key("elytrarace", "test_cup");
        net.kyori.adventure.text.Component displayName = net.kyori.adventure.text.Component.text("Test Cup");
        Key mapId = Key.key("elytrarace", "test_map");

        // Set the current cup
        gameStateSystem.setCurrentCup(gameStateEntity, cupId, displayName, java.util.List.of(mapId));

        // Set the current map
        gameStateSystem.setCurrentMap(gameStateEntity, mapId, "world", 0);

        getLogger().info("Game initialized with test cup and map");
    }

    @Override
    public void onDisable() {
        getLogger().info("SimpleElytraRace has been disabled!");
    }

    @Override
    public @org.jetbrains.annotations.Nullable ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @org.jetbrains.annotations.Nullable String id) {
        return new VoidGenProvider();
    }
}
