package net.elytrarace.voyager.server.game;

import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.chunk.ChunkCache;

import org.jetbrains.annotations.Nullable;

/**
 * The block reads the flight simulation makes, pointed at whichever map's world is currently being
 * played.
 *
 * <h2>Why this indirection exists</h2>
 *
 * <p>{@code FlightTickDriver} is constructed once with one {@code CollisionSpace}, and
 * {@code MinestomCollisionSpace} is constructed once with one {@link Block.Getter} — but the world
 * changes every time the cup advances to the next map. Rebuilding the driver per map would throw
 * away the per-player simulated flight state it holds, which is the one thing that must survive a
 * tick. So the space stays and the world behind it moves.
 *
 * <h2>Why {@link ChunkCache} and not {@code Instance#getBlock}</h2>
 *
 * <p>{@code Instance#getBlock} throws {@code NullPointerException} for an unloaded chunk, and
 * {@code Chunk#getBlock} asserts that the caller holds the chunk's read lock. A racer's collision
 * query sweeps a box that can straddle a chunk edge at the moment the far side has not arrived, and
 * a physics tick that throws is a server that stops ticking. {@link ChunkCache} is Minestom's own
 * answer: it caches the last chunk, takes the read lock, and returns its default block when the
 * chunk is not loaded. Constructed with a {@code null} default on purpose — that is exactly the
 * {@code null} {@code MinestomCollisionSpace} documents as "skip this cell", matching Vanilla's
 * {@code BlockCollisions.computeNext} skipping a cell whose chunk is {@code null}. A default of
 * {@code Block.AIR} would instead assert that the unloaded cell is empty, which is a claim about a
 * racetrack nobody has read yet.
 *
 * <p>{@code ChunkCache} carries {@code @ApiStatus.Internal} in Minestom's own javadoc.
 * {@code MinestomCollisionSpace}'s constructor javadoc names it as the production implementation, so
 * this is the intended use rather than a reach past an interface; it is recorded here because an
 * internal type is a type that can move under us.
 */
final class CurrentMapBlocks implements Block.Getter {

    private @Nullable ChunkCache cache;

    /**
     * Points the simulation at {@code instance}, or at nothing when {@code null} — between maps, and
     * before the first one. A fresh cache per world, because the cached chunk belongs to the world it
     * came from.
     */
    void follow(@Nullable Instance instance) {
        this.cache = instance == null ? null : new ChunkCache(instance, null, null);
    }

    /**
     * Whether a world is currently being read. {@code CupSession.describe()} reports it, because
     * "the simulation is reading nothing" and "the simulation is reading an empty stretch of sky"
     * produce identical output otherwise — and the first is a wiring fault.
     */
    boolean hasWorld() {
        return cache != null;
    }

    @Override
    public @Nullable Block getBlock(int x, int y, int z, Condition condition) {
        ChunkCache current = cache;
        // No world yet is the same answer as no chunk yet: nothing is known about this cell, so the
        // collision space skips it rather than treating it as air it has seen.
        return current == null ? null : current.getBlock(x, y, z, condition);
    }
}
