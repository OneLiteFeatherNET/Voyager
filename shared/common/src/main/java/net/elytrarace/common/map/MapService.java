package net.elytrarace.common.map;

import net.elytrarace.common.cup.model.CupDTO;
import net.elytrarace.common.map.model.MapDTO;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public interface MapService {

    CompletableFuture<MapDTO> getMapByName(@NotNull String name);

    CompletableFuture<CupDTO> getMapByCup(@NotNull CupDTO cupDTO);

    @Contract("_ -> new")
    static MapService create(@NotNull JavaPlugin plugin) {
        return new MapServiceImpl(plugin);
    }
}
