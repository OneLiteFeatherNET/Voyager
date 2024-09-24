package net.elytrarace.common.map;

import net.elytrarace.common.cup.model.CupDTO;
import net.elytrarace.common.cup.model.FileCupDTO;
import net.elytrarace.common.cup.model.ResolvedCupDTO;
import net.elytrarace.common.map.model.MapDTO;
import net.elytrarace.common.utils.GsonUtil;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
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
}
