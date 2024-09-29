package net.elytrarace.game;

import net.elytrarace.common.language.LanguageService;
import net.elytrarace.game.service.GameService;
import net.elytrarace.game.util.ElytraMarkers;
import net.elytrarace.game.world.VoidGenProvider;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;

public class ElytraRace extends JavaPlugin {

    @Override
    public void onEnable() {
        try {
            Files.createDirectories(getDataPath());
        } catch (IOException e) {
            getLogger().warning("Unable to create plugin directory");
        }
        LanguageService
                .create("elytrarace", Key.key("elytrarace", "language"), this)
                .loadLanguage()
                .thenRun(() -> getLogger().info("Language has been loaded"));
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
