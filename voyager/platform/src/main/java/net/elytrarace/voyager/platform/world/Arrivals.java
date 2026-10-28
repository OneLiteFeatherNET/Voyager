package net.elytrarace.voyager.platform.world;

import net.minestom.server.coordinate.ChunkRange;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.RelativeFlags;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The arrival step a move within one world shares: a racer is moved onto a spawn only once the chunks around it are
 * there, and the teleport is confirmed by the client before its position packets are believed again.
 *
 * <p>A move between two worlds is {@link PendingArrival}'s, which does not block the tick. {@link #reposition} is the one
 * move that still waits: a reset inside a map, which is a single racer moved within a world they already stand in. It is
 * noted for a later change, and it keeps its join.
 */
public final class Arrivals {

    private Arrivals() {
    }

    /**
     * Moves {@code player} to {@code target} within the world they already stand in. The chunks around the target are
     * loaded first, then the teleport is sent and the wait for its chunks completes.
     *
     * <p>Returning means the teleport has been <em>sent</em>, not confirmed: the call does not wait for the client to
     * echo the teleport id back. Until it does, the server discards that client's movement packets.
     *
     * <p>The player must already be in an instance; the target is in that instance too.
     */
    public static void reposition(Player player, Pos target) {
        awaitChunksAround(player.getInstance(), target, player.effectiveViewDistance());
        player.teleport(target, null, RelativeFlags.NONE, true).join();
    }

    /**
     * Loads every chunk within {@code viewDistance} of {@code spawn} and returns once they are all there.
     */
    private static void awaitChunksAround(Instance instance, Pos spawn, int viewDistance) {
        List<CompletableFuture<Chunk>> pending = new ArrayList<>();
        ChunkRange.chunksInRange(spawn, viewDistance, (chunkX, chunkZ) -> {
            CompletableFuture<Chunk> chunk = instance.loadOptionalChunk(chunkX, chunkZ);
            if (!chunk.isDone() || chunk.isCompletedExceptionally()) {
                pending.add(chunk);
            }
        });
        CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).join();
    }
}
