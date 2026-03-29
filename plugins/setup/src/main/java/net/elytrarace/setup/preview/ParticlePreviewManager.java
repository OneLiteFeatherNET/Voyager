package net.elytrarace.setup.preview;

import net.elytrarace.common.map.MapService;
import net.elytrarace.setup.util.SetupGuard;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player portal particle preview and spline path visualization.
 * Runs a repeating task that renders portal outlines and the spline ideal line.
 */
public final class ParticlePreviewManager {

    private static final long RENDER_INTERVAL_TICKS = 10L; // every 0.5 sec

    private final Set<UUID> portalPreviewPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> splinePreviewPlayers = ConcurrentHashMap.newKeySet();
    private final MapService mapService;
    private BukkitTask task;

    public ParticlePreviewManager(MapService mapService) {
        this.mapService = mapService;
    }

    public void start(Plugin plugin) {
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::renderAll, 0L, RENDER_INTERVAL_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        portalPreviewPlayers.clear();
        splinePreviewPlayers.clear();
    }

    /** Toggles portal outline preview. Returns true if now enabled. */
    public boolean togglePortals(UUID playerId) {
        if (portalPreviewPlayers.remove(playerId)) return false;
        portalPreviewPlayers.add(playerId);
        return true;
    }

    /** Toggles spline path preview. Returns true if now enabled. */
    public boolean toggleSpline(UUID playerId) {
        if (splinePreviewPlayers.remove(playerId)) return false;
        splinePreviewPlayers.add(playerId);
        return true;
    }

    public void remove(UUID playerId) {
        portalPreviewPlayers.remove(playerId);
        splinePreviewPlayers.remove(playerId);
    }

    private void renderAll() {
        var allPlayers = new HashSet<UUID>();
        allPlayers.addAll(portalPreviewPlayers);
        allPlayers.addAll(splinePreviewPlayers);

        for (var playerId : allPlayers) {
            var player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                remove(playerId);
                continue;
            }

            if (SetupGuard.getSetupHolder(player).isEmpty()) {
                remove(playerId);
                continue;
            }

            var mapOpt = SetupGuard.getMapForWorld(mapService, player.getWorld());
            if (mapOpt.isEmpty()) continue;
            var map = mapOpt.get();

            // Render portal outlines
            if (portalPreviewPlayers.contains(playerId)) {
                for (var portal : map.portals()) {
                    ParticleRenderer.renderPortal(player, portal.locations(), portal.index());
                }
            }

            // Render spline ideal line
            if (splinePreviewPlayers.contains(playerId)) {
                var splinePoints = SplineRenderer.generateSplinePoints(map.portals());
                SplineRenderer.renderSpline(player, splinePoints);
            }
        }
    }
}
