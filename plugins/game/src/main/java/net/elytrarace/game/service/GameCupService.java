package net.elytrarace.game.service;

import net.elytrarace.common.cup.model.CupDTO;
import net.elytrarace.common.cup.model.ResolvedCupDTO;
import net.elytrarace.game.model.GameMapDTO;
import net.elytrarace.game.model.GamePortalDTO;
import net.elytrarace.game.model.GameSession;
import net.elytrarace.game.util.ElytraMarkers;
import net.elytrarace.game.util.PluginInstanceHolder;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class GameCupService {

    private static final ComponentLogger LOGGER = ComponentLogger.logger(GameCupService.class);

    static synchronized CompletableFuture<ResolvedCupDTO> startLoadingWorldAsync(CupDTO cup) {
        if (cup == null) {
            LOGGER.error(ElytraMarkers.MAP, "The map could not be loaded");
            return null;
        }
        LOGGER.info(ElytraMarkers.CUP, "The cup has been loaded");
        LOGGER.info(ElytraMarkers.CUP, "Setting the current cup to: {}", cup.name());
        if (cup instanceof ResolvedCupDTO resolvedCup) {
            return CompletableFuture.completedFuture(resolvedCup)
                    .thenCompose(GameCupService::loadMaps);
        }
        return null;
    }

    private synchronized static @NotNull CompletionStage<ResolvedCupDTO> loadMaps(ResolvedCupDTO dto) {
        var allBukkitMapsLoaded = dto.maps().stream().map(map -> {
            LOGGER.info("Loaded map: {}", map.name());
            return CompletableFuture.completedFuture(map).thenApplyAsync(mapDTO -> WorldCreator
                            .name(mapDTO.world())
                            .generator("ElytraRace")
                            .environment(World.Environment.NORMAL)
                            .type(WorldType.FLAT)
                            .keepSpawnLoaded(TriState.FALSE)
                            .createWorld(), Bukkit.getScheduler().getMainThreadExecutor(PluginInstanceHolder.getPluginInstance()))
                    .thenAccept(world -> LOGGER.info(ElytraMarkers.MAP, "Loaded world: {}", world.getName()))
                    .exceptionally(throwable -> {
                        LOGGER.error(ElytraMarkers.EXCEPTION, "An error occurred while loading the world", throwable);
                        return null;
                    });
        }).toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(allBukkitMapsLoaded).thenApply(ignored -> dto);
    }

    static synchronized GameSession switchMapInternal(GameSession gameSession) {
        if (gameSession.currentCup() == null) {
            LOGGER.error(ElytraMarkers.CUP, "No cup has been set, shutting down server...");
            Bukkit.shutdown();
            return null;
        }
        if (gameSession.currentMap() == null) {
            return GameSession.fromWithCurrentMap(gameSession, (GameMapDTO) gameSession.currentCup().maps().getFirst());
        }
        var currentMap = gameSession.currentMap();
        currentMap.portals().stream().map(GamePortalDTO.class::cast).filter(Objects::nonNull).forEach(GamePortalDTO::despawn);
        var index = gameSession.currentCup().maps().indexOf(currentMap);
        var nextIndex = index + 1;
        if (nextIndex >= gameSession.currentCup().maps().size()) {
            return GameSession.fromWithCurrentMap(gameSession, null);
        }
        return GameSession.fromWithCurrentMap(gameSession, (GameMapDTO) gameSession.currentCup().maps().get(nextIndex));
    }
}
