package net.elytrarace.voyager.platform.world;

import net.elytrarace.voyager.platform.world.exception.ArrivalFailedException;
import net.minestom.server.coordinate.ChunkRange;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.RelativeFlags;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * The arrival step every move between spawns shares: a racer goes into an instance, or within one, only once the
 * chunks around the spawn are there and the teleport is confirmed.
 *
 * <p>Both the map transition and the lobby transition move racers this way, so the hazard and its two guards are
 * written down once, here. The failure being avoided is Minestom issue #2017: a player moved before the chunks are
 * there has no ground under the client, and falls. {@link #arrive} waits for the chunks before it moves the player,
 * because Minestom's own {@code setInstance} waits on a latch that only a successful load releases, and a failed chunk
 * there would block the tick thread for good. The teleport is confirmed by the client before its position packets
 * are believed again.
 *
 * <p>Calls block, and that is deliberate: the racers are moved on the tick thread, once per move, with nobody flying.
 */
public final class Arrivals {

    private Arrivals() {
    }

    /**
     * Moves {@code player} onto {@code spawn} in {@code target}, the instance of the world named {@code world}.
     *
     * <p>Identity, not equality: {@link MapInstances} hands out one instance object per world name, so "already in this
     * world" is "the same object", and Minestom's own {@code setInstance} guard compares the same way. A player already
     * in {@code target} is repositioned within it.
     *
     * @param player the racer to move
     * @param world  the name of the world {@code target} holds, for the message of a failure
     * @param target the instance to move into
     * @param spawn  where the racer stands once the move returns
     * @throws ArrivalFailedException if a chunk around the spawn fails to load; the racer is not moved
     */
    public static void arrive(Player player, String world, Instance target, Pos spawn) {
        if (player.getInstance() == target) {
            reposition(player, spawn);
            return;
        }
        // The range is the one setInstance itself uses: its view distance is the player's setting capped by the
        // target instance's, which is not the view distance the player has in their current instance. Loading the
        // chunks first means setInstance finds them all present and spawns the racer synchronously.
        int viewDistance = Math.min(player.getSettings().viewDistance(), target.viewDistance()) + 1;
        try {
            awaitChunksAround(target, spawn, viewDistance);
        } catch (CompletionException exception) {
            throw new ArrivalFailedException(world, spawn, exception.getCause());
        }
        player.setInstance(target, spawn).join();
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
