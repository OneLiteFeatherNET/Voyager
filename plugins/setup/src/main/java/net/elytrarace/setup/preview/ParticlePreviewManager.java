package net.elytrarace.setup.preview;

import net.elytrarace.common.map.MapService;
import net.elytrarace.common.map.model.FileMapDTO;
import net.elytrarace.common.map.model.PortalDTO;
import net.elytrarace.setup.ElytraRace;
import net.elytrarace.setup.util.SetupGuard;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player portal particle preview. Runs a repeating task that renders
 * portal outlines as particles for players who have toggled preview on.
 */
public final class ParticlePreviewManager {

    private static final long RENDER_INTERVAL_TICKS = 10L; // every 0.5 sec

    private final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();
    private final MapService mapService;
    private BukkitTask task;

    public ParticlePreviewManager(MapService mapService) {
        this.mapService = mapService;
    }

    /**
     * Starts the repeating render task.
     */
    public void start(Plugin plugin) {
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::renderAll, 0L, RENDER_INTERVAL_TICKS);
    }

    /**
     * Stops the render task and clears all active players.
     */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        activePlayers.clear();
    }

    /**
     * Toggles preview for a player. Returns true if preview is now enabled.
     */
    public boolean toggle(UUID playerId) {
        if (activePlayers.contains(playerId)) {
            activePlayers.remove(playerId);
            return false;
        }
        activePlayers.add(playerId);
        return true;
    }

    /**
     * Removes a player (e.g., on quit).
     */
    public void remove(UUID playerId) {
        activePlayers.remove(playerId);
    }

    private void renderAll() {
        for (var playerId : activePlayers) {
            var player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                activePlayers.remove(playerId);
                continue;
            }

            // Only render if player is still in setup mode
            if (SetupGuard.getSetupHolder(player).isEmpty()) {
                activePlayers.remove(playerId);
                continue;
            }

            var mapOpt = SetupGuard.getMapForWorld(mapService, player.getWorld());
            if (mapOpt.isEmpty()) continue;

            var map = mapOpt.get();
            for (var portal : map.portals()) {
                ParticleRenderer.renderPortal(player, portal.locations(), portal.index());
            }
        }
    }
}
