package net.elytrarace.common.map.model;

/**
 * A guide point for shaping the ideal racing line between portals.
 * Stored per-map alongside portals.
 *
 * @param orderIndex  global ordering among all path points (portals use index*100, guide points fill gaps)
 * @param x           block X coordinate
 * @param y           block Y coordinate
 * @param z           block Z coordinate
 */
public record GuidePointDTO(
        int orderIndex,
        double x,
        double y,
        double z
) implements Comparable<GuidePointDTO> {

    @Override
    public int compareTo(GuidePointDTO other) {
        return Integer.compare(this.orderIndex, other.orderIndex);
    }
}
