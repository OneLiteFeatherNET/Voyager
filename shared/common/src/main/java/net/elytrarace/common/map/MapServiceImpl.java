package net.elytrarace.common.map;

import net.elytrarace.common.cup.model.CupDTO;
import net.elytrarace.common.cup.model.FileCupDTO;
import net.elytrarace.common.cup.model.ResolvedCupDTO;
import net.elytrarace.common.map.model.MapDTO;
import net.elytrarace.common.utils.GsonUtil;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

class MapServiceImpl implements MapService {

    private final MapProvider mapProvider;

    MapServiceImpl(@NotNull JavaPlugin plugin) {
        mapProvider = new MapProvider(GsonUtil.GSON, plugin.getDataPath(), ArrayList::new);
    }

    @Override
    public CompletableFuture<MapDTO> getMapByName(@NotNull String name) {
        return CompletableFuture.supplyAsync(() -> this.mapProvider.getMapsAsList().stream()
                .filter(mapDTO -> mapDTO.name().equals(name))
                .findFirst()
                .orElse(null));
    }

    @Override
    public CompletableFuture<CupDTO> getMapByCup(@NotNull CupDTO cupDTO) {
        if (!(cupDTO instanceof FileCupDTO fileCupDTO)) {
            throw new IllegalArgumentException("CupDTO must be an instance of FileCupDTO");
        }
        return CompletableFuture.supplyAsync(() -> fileCupDTO.maps().stream()
                .flatMap(uuid -> this.mapProvider.getMaps()
                        .stream()
                        .filter(mapDTO -> mapDTO.uuid().equals(uuid))
                )
                .toList())
                .thenApply(maps -> new ResolvedCupDTO(fileCupDTO.name(), fileCupDTO.displayName(), maps));

    }

    @Override
    public CompletableFuture<MapDTO> getMapByUUID(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> this.mapProvider.getMapsAsList().stream()
                .filter(mapDTO -> mapDTO.uuid().equals(uuid))
                .findFirst()
                .orElse(null));
    }

    @Override
    public CompletableFuture<Boolean> addMap(@NotNull MapDTO mapDTO) {
        return CompletableFuture.supplyAsync(() -> {
            if (this.mapProvider.getMaps().stream().anyMatch(cup -> cup.name().equals(mapDTO.name()))) {
                return false;
            }
            this.mapProvider.addMap(mapDTO);
            return true;
        });
    }

    @Override
    public CompletableFuture<Boolean> removeMap(@NotNull MapDTO mapDTO) {
        return CompletableFuture.supplyAsync(() -> {
            if (this.mapProvider.getMaps().stream().noneMatch(cup -> cup.name().equals(mapDTO.name()))) {
                return false;
            }
            this.mapProvider.removeMap(mapDTO);
            return true;
        });
    }

    @Override
    public CompletableFuture<Boolean> updateMap(@NotNull MapDTO mapDTO) {
        return CompletableFuture.supplyAsync(() -> {
            if (this.mapProvider.getMaps().stream().noneMatch(cup -> cup.name().equals(mapDTO.name()))) {
                return false;
            }
            this.mapProvider.removeMap(mapDTO);
            this.mapProvider.addMap(mapDTO);
            return true;
        });
    }

    @Override
    public List<MapDTO> getMaps() {
        return this.mapProvider.getMapsAsList();
    }

    @Override
    public CompletableFuture<List<MapDTO>> getMapsAsync() {
        return CompletableFuture.supplyAsync(this::getMaps);
    }

    @Override
    public CompletableFuture<Void> saveMaps() {
        return CompletableFuture.runAsync(this.mapProvider::saveMaps);
    }
}
