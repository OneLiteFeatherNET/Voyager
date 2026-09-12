package net.elytrarace.voyager.platform.collision;

import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A world of a handful of blocks, with no server behind it.
 *
 * <p>It also records every coordinate it is asked for, in order, which is the only way to observe
 * the grid {@link MinestomCollisionSpace} walks at the cells where no block stands — and those
 * cells are most of the grid.
 */
final class FakeBlockGetter implements Block.Getter {

    record Cell(int x, int y, int z) {
    }

    private final Map<Cell, Block> blocks = new HashMap<>();
    private final List<Cell> visited = new ArrayList<>();
    private final @Nullable Block background;

    private FakeBlockGetter(@Nullable Block background) {
        this.background = background;
    }

    /** A world of air, into which blocks can be placed. */
    static FakeBlockGetter ofAir() {
        return new FakeBlockGetter(Block.AIR);
    }

    /**
     * A world that answers {@code null} everywhere, as {@code ChunkCache} does for an unloaded chunk
     * when it was built without a default block.
     */
    static FakeBlockGetter unloaded() {
        return new FakeBlockGetter(null);
    }

    FakeBlockGetter with(int x, int y, int z, Block block) {
        blocks.put(new Cell(x, y, z), block);
        return this;
    }

    @Override
    public @Nullable Block getBlock(int x, int y, int z, Condition condition) {
        Cell cell = new Cell(x, y, z);
        visited.add(cell);
        Block placed = blocks.get(cell);
        return placed != null ? placed : background;
    }

    List<Cell> visited() {
        return List.copyOf(visited);
    }
}
