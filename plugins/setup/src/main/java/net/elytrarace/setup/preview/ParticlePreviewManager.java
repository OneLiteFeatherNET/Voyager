package net.elytrarace.setup.preview;

import net.elytrarace.common.map.MapService;
import net.elytrarace.common.guide.GuidePointStore;
import net.elytrarace.setup.util.SetupGuard;
import net.elytrarace.spline.SplineConfig;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player portal particle preview, TextDisplay labels,
 * and spline path visualization.
 */
public final class ParticlePreviewManager {

    private static final long RENDER_INTERVAL_TICKS = 10L;

    private final Set<UUID> portalPreviewPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> splinePreviewPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, SplineConfig> playerConfigs = new ConcurrentHashMap<>();
    private final MapService mapService;
    private final GuidePointStore guideStore;
    private final PortalLabelManager labelManager = new PortalLabelManager();
    private BukkitTask task;

    public ParticlePreviewManager(MapService mapService, GuidePointStore guideStore) {
        this.mapService = mapService;
        this.guideStore = guideStore;
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
        playerConfigs.clear();
        labelManager.removeAll();
    }

    /**
     * Toggles portal preview (particles + TextDisplay labels).
     * Returns true if now enabled.
     */
    public boolean togglePortals(UUID playerId) {
        if (portalPreviewPlayers.remove(playerId)) {
            // Disabled — remove labels if no other player has preview active in that world
            var player = Bukkit.getPlayer(playerId);
            if (player != null) {
                var worldName = player.getWorld().getName();
                boolean othersActive = portalPreviewPlayers.stream()
                        .map(Bukkit::getPlayer)
                        .anyMatch(p -> p != null && p.getWorld().getName().equals(worldName));
                if (!othersActive) {
                    labelManager.removeLabels(player.getWorld());
                }
            }
            return false;
        }
        portalPreviewPlayers.add(playerId);
        // Spawn labels immediately
        var player = Bukkit.getPlayer(playerId);
        if (player != null) {
            var mapOpt = SetupGuard.getMapForWorld(mapService, player.getWorld());
            mapOpt.ifPresent(map -> labelManager.spawnLabels(player.getWorld(), map.portals()));
        }
        return true;
    }

    public boolean toggleSpline(UUID playerId) {
        if (splinePreviewPlayers.remove(playerId)) return false;
        splinePreviewPlayers.add(playerId);
        return true;
    }

    public boolean hasPortalPreview(UUID playerId) {
        return portalPreviewPlayers.contains(playerId);
    }

    public boolean hasSplinePreview(UUID playerId) {
        return splinePreviewPlayers.contains(playerId);
    }

    public SplineConfig getConfig(UUID playerId) {
        return playerConfigs.getOrDefault(playerId, SplineConfig.BUILDER);
    }

    public void setConfig(UUID playerId, SplineConfig config) {
        playerConfigs.put(playerId, config);
    }

    /**
     * Refreshes TextDisplay labels for a world (call after portal changes).
     */
    public void refreshLabels(String worldName) {
        var world = Bukkit.getWorld(worldName);
        if (world == null) return;
        // Only refresh if any player has portal preview active in this world
        boolean anyActive = portalPreviewPlayers.stream()
                .map(Bukkit::getPlayer)
                .anyMatch(p -> p != null && p.getWorld().getName().equals(worldName));
        if (anyActive) {
            var mapOpt = SetupGuard.getMapForWorld(mapService, world);
            mapOpt.ifPresent(map -> labelManager.spawnLabels(world, map.portals()));
        }
    }

    public void remove(UUID playerId) {
        portalPreviewPlayers.remove(playerId);
        splinePreviewPlayers.remove(playerId);
        playerConfigs.remove(playerId);
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

            if (portalPreviewPlayers.contains(playerId)) {
                for (var portal : map.portals()) {
                    ParticleRenderer.renderPortal(player, portal.locations(), portal.index());
                }
            }

            if (splinePreviewPlayers.contains(playerId)) {
                var config = getConfig(playerId);
                var guidePoints = guideStore.getGuidePoints(map.world());
                var splinePoints = SplineRenderer.generateSplinePoints(map.portals(), guidePoints, config);
                SplineRenderer.renderSpline(player, splinePoints, config);
                SplineRenderer.renderGuidePoints(player, guidePoints);
            }
        }
    }
}
