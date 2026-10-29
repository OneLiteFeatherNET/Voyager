package net.elytrarace.voyager.api.mapsetup;

import net.elytrarace.voyager.api.math.Vec3;

/**
 * The spawn a world states in its {@code level.dat}, as the block it names.
 *
 * @param x      the block's x coordinate
 * @param y      the block's y coordinate
 * @param z      the block's z coordinate
 * @param source the key the world stated the spawn under
 */
public record StatedSpawn(int x, int y, int z, Source source) {

    /** Where the world states its spawn. */
    public enum Source {
        /** The modern {@code Data.spawn} compound of the overworld. */
        MODERN,
        /** The legacy {@code Data.SpawnX}, {@code SpawnY} and {@code SpawnZ} keys. */
        LEGACY
    }

    /**
     * The spawn a draft takes: the centre of the stated block, on its x and z, at the block's y.
     *
     * @return the spawn position
     */
    public Vec3 centre() {
        return new Vec3(x + 0.5, y, z + 0.5);
    }

    /**
     * Whether the block sits on the default-looking column {@code x = 0, z = 0}. A world that never had its spawn set
     * usually states that column, so the reply asks the builder to check it.
     *
     * @return true when both x and z are 0
     */
    public boolean looksDefault() {
        return x == 0 && z == 0;
    }
}
