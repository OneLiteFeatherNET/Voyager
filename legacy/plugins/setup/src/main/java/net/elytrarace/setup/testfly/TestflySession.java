package net.elytrarace.setup.testfly;

import net.elytrarace.common.collision.PortalCollisionHelper;
import net.elytrarace.common.map.model.PortalDTO;
import org.apache.commons.geometry.euclidean.threed.Vector3D;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player state for an active test flight.
 */
public final class TestflySession {

    private final UUID playerId;
    private final List<PortalDTO> sortedPortals;
    private final List<PortalCollisionHelper.PortalGeometry> geometries;
    private final ItemStack[] savedInventory;
    private final ItemStack[] savedArmor;
    private final long startTimeMs;
    private final Set<Integer> hitPortals = new HashSet<>();

    // Ring buffer for last 3 positions
    private Vector3D pos1; // oldest
    private Vector3D pos2;
    private Vector3D pos3; // newest
    private int posCount = 0;

    public TestflySession(UUID playerId, List<PortalDTO> sortedPortals,
                          ItemStack[] savedInventory, ItemStack[] savedArmor) {
        this.playerId = playerId;
        this.sortedPortals = sortedPortals;
        this.geometries = sortedPortals.stream()
                .map(p -> PortalCollisionHelper.buildGeometry(p.locations()))
                .toList();
        this.savedInventory = savedInventory;
        this.savedArmor = savedArmor;
        this.startTimeMs = System.currentTimeMillis();
    }

    /**
     * Updates the position ring buffer. Call every tick.
     */
    public void updatePosition(double x, double y, double z) {
        pos1 = pos2;
        pos2 = pos3;
        pos3 = Vector3D.of(x, y, z);
        posCount = Math.min(posCount + 1, 3);
    }

    /**
     * Checks all un-hit portals against the current position buffer.
     * Returns the index of a newly hit portal, or -1 if none.
     */
    public int checkCollisions() {
        if (posCount < 3) return -1;

        for (int i = 0; i < sortedPortals.size(); i++) {
            if (hitPortals.contains(i)) continue;

            if (PortalCollisionHelper.checkIntersection(geometries.get(i), pos1, pos2, pos3)) {
                hitPortals.add(i);
                return sortedPortals.get(i).index();
            }
        }
        return -1;
    }

    public UUID playerId() { return playerId; }
    public int hitCount() { return hitPortals.size(); }
    public int totalPortals() { return sortedPortals.size(); }
    public boolean isComplete() { return hitPortals.size() >= sortedPortals.size(); }
    public ItemStack[] savedInventory() { return savedInventory; }
    public ItemStack[] savedArmor() { return savedArmor; }

    public long elapsedMs() { return System.currentTimeMillis() - startTimeMs; }

    public String elapsedFormatted() {
        long ms = elapsedMs();
        long secs = ms / 1000;
        long tenths = (ms % 1000) / 100;
        return secs + "." + tenths + "s";
    }

    /**
     * Returns portal indices that were missed (not hit).
     */
    public List<Integer> missedPortalIndices() {
        var all = new java.util.ArrayList<Integer>();
        for (int i = 0; i < sortedPortals.size(); i++) {
            if (!hitPortals.contains(i)) {
                all.add(sortedPortals.get(i).index());
            }
        }
        return all;
    }
}
