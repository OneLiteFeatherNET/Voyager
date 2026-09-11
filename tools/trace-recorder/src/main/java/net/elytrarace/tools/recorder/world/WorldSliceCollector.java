package net.elytrarace.tools.recorder.world;

import net.elytrarace.tools.recorder.format.BlockBox;
import net.elytrarace.tools.recorder.format.TraceTick;
import org.jetbrains.annotations.ApiStatus;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Collects the world slice a recorded path needs to be replayed against.
 *
 * <p>The slice is captured along the flown path rather than over a fixed region: for every tick,
 * the cube of blocks within {@code ceil(radius)} of the entity's block position is scanned and every
 * solid block found is emitted as a unit {@link BlockBox}. That keeps a fixture small and
 * self-contained while still letting the replay resolve the same collisions the recording did.
 */
@ApiStatus.Internal
public abstract class WorldSliceCollector {

    private WorldSliceCollector() {
    }

    public static List<BlockBox> collect(List<TraceTick> ticks, double radius, SolidBlockSource source) {
        int reach = (int) Math.ceil(radius);
        Set<BlockBox> slice = new LinkedHashSet<>();
        for (TraceTick tick : ticks) {
            int blockX = (int) Math.floor(tick.posX());
            int blockY = (int) Math.floor(tick.posY());
            int blockZ = (int) Math.floor(tick.posZ());
            for (int x = blockX - reach; x <= blockX + reach; x++) {
                for (int y = blockY - reach; y <= blockY + reach; y++) {
                    for (int z = blockZ - reach; z <= blockZ + reach; z++) {
                        if (source.isSolid(x, y, z)) {
                            slice.add(new BlockBox(x, y, z, x + 1, y + 1, z + 1));
                        }
                    }
                }
            }
        }
        return List.copyOf(slice);
    }
}
