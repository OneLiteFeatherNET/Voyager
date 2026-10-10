package net.elytrarace.setup.util;

import net.elytrarace.common.cup.CupService;
import net.elytrarace.common.map.MapService;
import net.elytrarace.common.map.model.PortalDTO;
import org.bukkit.Bukkit;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Provides tab-completion suggestions for setup commands.
 */
public final class SetupSuggestions {

    private SetupSuggestions() {}

    /**
     * Suggests map names from the MapService.
     */
    public static BlockingSuggestionProvider.Strings<Source> mapNames(MapService mapService) {
        return (ctx, input) -> mapService.getMaps().stream()
                .map(m -> m.name().value())
                .toList();
    }

    /**
     * Suggests cup names from the CupService.
     */
    public static BlockingSuggestionProvider.Strings<Source> cupNames(CupService cupService) {
        return (ctx, input) -> cupService.getCups().stream()
                .map(c -> c.name().value())
                .toList();
    }

    /**
     * Suggests portal indices for the player's current map.
     */
    public static BlockingSuggestionProvider.Strings<Source> portalIndices(MapService mapService) {
        return (ctx, input) -> {
            var sender = ctx.sender();
            if (!(sender instanceof org.incendo.cloud.paper.util.sender.PlayerSource playerSource)) {
                return java.util.List.of();
            }
            var player = playerSource.source();
            return SetupGuard.getMapForWorld(mapService, player.getWorld())
                    .map(map -> map.portals().stream()
                            .map(PortalDTO::index)
                            .map(String::valueOf)
                            .toList())
                    .orElse(java.util.List.of());
        };
    }

    /**
     * Suggests world folder names from the server directory.
     */
    public static BlockingSuggestionProvider.Strings<Source> worldFolders() {
        return (ctx, input) -> {
            var serverDir = Bukkit.getWorldContainer().toPath();
            try (Stream<Path> paths = Files.list(serverDir)) {
                return paths.filter(Files::isDirectory)
                        .filter(p -> Files.exists(p.resolve("level.dat"))) // only real world folders
                        .map(p -> p.getFileName().toString())
                        .toList();
            } catch (IOException e) {
                return java.util.List.of();
            }
        };
    }
}
