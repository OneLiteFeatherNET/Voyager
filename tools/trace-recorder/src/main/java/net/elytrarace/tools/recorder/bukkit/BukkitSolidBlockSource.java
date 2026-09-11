package net.elytrarace.tools.recorder.bukkit;

import net.elytrarace.tools.recorder.world.SolidBlockSource;
import org.bukkit.World;

/** Wraps a live Bukkit {@link World}, answering {@link #isSolid} from the block actually loaded there. */
public final class BukkitSolidBlockSource implements SolidBlockSource {

    private final World world;

    public BukkitSolidBlockSource(World world) {
        this.world = world;
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        return world.getBlockAt(x, y, z).getType().isSolid();
    }
}
