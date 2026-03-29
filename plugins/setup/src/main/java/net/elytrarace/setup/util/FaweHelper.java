package net.elytrarace.setup.util;

import com.fastasyncworldedit.core.regions.PolyhedralRegion;
import com.fastasyncworldedit.core.regions.selector.PolyhedralRegionSelector;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extension.platform.permission.ActorSelectorLimits;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.RegionSelector;
import net.elytrarace.common.map.model.LocationDTO;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Utility for accessing FAWE region data from a player's WorldEdit session.
 */
public final class FaweHelper {

    private FaweHelper() {}

    /**
     * Gets the player's current FAWE region selector if it is a PolyhedralRegionSelector.
     */
    public static Optional<PolyhedralRegionSelector> getPolyhedralSelector(Player player) {
        var actor = BukkitAdapter.adapt(player);
        var localSession = actor.getSession();
        var world = actor.getWorld();
        RegionSelector regionSelector = localSession.getRegionSelector(world);
        if (regionSelector instanceof PolyhedralRegionSelector polyhedralSelector) {
            return Optional.of(polyhedralSelector);
        }
        return Optional.empty();
    }

    /**
     * Resets the player's FAWE selection to a fresh PolyhedralRegionSelector.
     */
    public static void resetToPolyhedralSelector(Player player) {
        var actor = BukkitAdapter.adapt(player);
        var localSession = actor.getSession();
        var world = actor.getWorld();
        localSession.setRegionSelector(world, new PolyhedralRegionSelector(world));
    }

    /**
     * Extracts vertices and center from a PolyhedralRegion into LocationDTOs.
     * Vertices get {@code center=false}, the region center gets {@code center=true}.
     */
    public static List<LocationDTO> extractLocations(PolyhedralRegion region) {
        List<LocationDTO> locations = new ArrayList<>();
        region.getVertices().forEach(vertex ->
                locations.add(new LocationDTO(vertex.x(), vertex.y(), vertex.z(), false))
        );
        Optional.ofNullable(region.getCenter())
                .map(center -> new LocationDTO(center.blockX(), center.blockY(), center.blockZ(), true))
                .ifPresent(locations::add);
        return locations;
    }

    /**
     * Loads portal vertices into a new PolyhedralRegionSelector on the player's session.
     * First non-center vertex becomes the primary selection, rest become secondary.
     */
    public static void setPolyhedralSelector(Player player, List<LocationDTO> locations) {
        var actor = BukkitAdapter.adapt(player);
        var localSession = actor.getSession();
        var world = actor.getWorld();
        var selector = new PolyhedralRegionSelector(world);
        var limits = ActorSelectorLimits.forActor(actor);

        var vertices = locations.stream().filter(loc -> !loc.center()).toList();
        for (int i = 0; i < vertices.size(); i++) {
            var loc = vertices.get(i);
            var vec = BlockVector3.at(loc.x(), loc.y(), loc.z());
            if (i == 0) {
                selector.selectPrimary(vec, limits);
            } else {
                selector.selectSecondary(vec, limits);
            }
        }

        localSession.setRegionSelector(world, selector);
    }

    /**
     * Computes the next auto-assigned portal index for a map.
     * Returns 1 if no portals exist, otherwise max(existing indices) + 1.
     */
    public static int nextPortalIndex(java.util.Collection<? extends net.elytrarace.common.map.model.PortalDTO> portals) {
        return portals.stream()
                .mapToInt(net.elytrarace.common.map.model.PortalDTO::index)
                .max()
                .orElse(0) + 1;
    }
}
