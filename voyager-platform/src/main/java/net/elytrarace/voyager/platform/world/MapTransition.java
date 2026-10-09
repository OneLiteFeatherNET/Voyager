package net.elytrarace.voyager.platform.world;

import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.minestom.server.coordinate.ChunkRange;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.RelativeFlags;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Moves every racer onto the next map: into its world, onto its spawn, with a fresh run.
 *
 * <p><strong>This is the operation the rebuild exists for.</strong> The tree being replaced loads
 * map two of a cup and never runs it; E3's state machine is the first that advances, and this is the
 * step that makes the players actually arrive there.
 *
 * <h2>The hazard: arriving before the ground does</h2>
 *
 * <p>Minestom issue <a href="https://github.com/Minestom/Minestom/issues/2017">#2017</a> — players
 * falling below the map on an instance switch — is closed without a framework fix; the reporter
 * solved it with a chunk check and confirmed teleports. Both mechanisms exist in
 * {@code 2026.08.28-26.2} and this class uses both <em>deliberately</em>, which is not the same as
 * assuming they happen:
 *
 * <ul>
 *   <li><strong>The chunks are waited for.</strong> {@link Player#setInstance(Instance, Pos)} loads
 *       every chunk within the player's effective view distance of the spawn before it spawns them
 *       (Player.java, the {@code ChunkRange.chunksInRange} loop in {@code setInstance}), but it
 *       reports that through a {@link CompletableFuture} and does the spawn itself on the scheduler's
 *       next process. A caller that fires and forgets has done nothing at all by the time it
 *       returns. {@link #advanceTo} blocks on that future, so "returned" means "arrived".</li>
 *   <li><strong>The teleport is confirmed.</strong> A confirmed teleport takes a teleport id, and
 *       until the client echoes it back through {@code ClientTeleportConfirmPacket}
 *       ({@code PacketListenerManager} wires it to
 *       {@code PlayerPositionListener::teleportConfirmListener}) every position packet from that
 *       client is discarded — {@code processMovement} returns early while
 *       {@code getLastSentTeleportId() != getLastReceivedTeleportId()}. That is what stops a
 *       position still describing the previous map from dragging the player back off the new
 *       spawn.</li>
 * </ul>
 *
 * <p>The failure being avoided is specific: a player moved before the chunks are there has no ground
 * under the client, and falls. In a flight game that reads as a physics bug and is not one.
 *
 * <h2>Two maps in one world need no switch at all</h2>
 *
 * <p>{@link MapInstances} caches one instance per world <em>name</em>, because one world backs more
 * than one map. Two maps naming the same world therefore resolve to the same instance, and
 * {@link Player#setInstance(Instance, Pos)} rejects that outright —
 * {@code Check.argCondition(currentInstance == instance, ...)}. So the same-world case takes the
 * other branch: the chunks around the new spawn are loaded here, explicitly, because a teleport
 * inside one instance loads only the destination chunk and the client needs the ones around it just
 * as much; then a confirmed teleport, written out in full rather than left to
 * {@link Player#teleport(Pos)}'s default, so a change to that default cannot silently turn the
 * confirmation off.
 *
 * <h2>Why this blocks</h2>
 *
 * <p>{@link #advanceTo} is synchronous, and that is deliberate. {@link RaceRuns} is single-threaded
 * like the rest of the tick path, so completing the move on a chunk-loader thread would write the
 * run map off the tick thread. Minestom supports the blocking shape directly — {@code setInstance}'s
 * returned future overrides {@code join()} to await the chunk latch and then drive the scheduler
 * when it is called from the thread that started the move, precisely so a caller on the tick thread
 * does not deadlock. A transition happens once per map, between phases, with nobody flying.
 *
 * <p>A player is given their run only once they have arrived, so a move that throws part-way leaves
 * the players it did reach holding a run for the map they are standing on, rather than every player
 * holding one for a map most of them never reached.
 */
public final class MapTransition {

    private final MapInstances instances;
    private final RaceRuns runs;

    /**
     * @param instances the world cache the map's world is resolved through
     * @param runs the per-player run state this transition empties and refills
     */
    public MapTransition(MapInstances instances, RaceRuns runs) {
        this.instances = instances;
        this.runs = runs;
    }

    /**
     * Moves every player to {@code map}'s spawn and gives each of them a run at the start of it.
     *
     * <p>Returns once every player has arrived: standing on the spawn, with the chunks around it
     * loaded and a confirmed teleport sent.
     *
     * <p>Every previously held run is dropped <em>before</em> anybody moves. A transition that moved
     * first and cleared afterwards would leave a window in which a run over the previous map could
     * be advanced with a position on the new one — passing rings the player never flew through.
     *
     * @param map the course being entered
     * @param players everyone taking part; a player already in the map's world is moved within it
     * @throws net.elytrarace.voyager.platform.world.exception.UnknownWorldException if the map names
     *     a world with no region data behind it
     */
    public void advanceTo(MapDefinition map, Collection<Player> players) {
        // Resolved before anything is dropped, so a map naming a world that is not there fails with
        // every racer still holding the run they had, rather than half-way into a move that cannot
        // finish.
        Instance target = instances.forWorld(map.world());
        // MapDefinition carries a position and no facing, so a racer arrives looking north. Giving
        // a spawn a yaw is a map-format change, not something to invent per player here.
        Pos spawn = Vectors.toMinestom(map.spawn()).asPos();

        runs.clearAll();

        for (Player player : players) {
            arrive(player, target, spawn);
            runs.startFresh(player.getUuid());
        }
    }

    /**
     * Identity, not equality: {@link MapInstances} hands out one instance object per world name, so
     * "already in this world" is "the same object", and Minestom's own {@code setInstance} guard
     * compares the same way.
     */
    private static void arrive(Player player, Instance target, Pos spawn) {
        if (player.getInstance() == target) {
            awaitChunksAround(target, spawn, player.effectiveViewDistance());
            player.teleport(spawn, null, RelativeFlags.NONE, true).join();
            return;
        }
        player.setInstance(target, spawn).join();
    }

    /**
     * Loads every chunk within {@code viewDistance} of {@code spawn} and returns once they are all
     * there — the same range {@code Player#setInstance} covers on the branch that does switch
     * instances, so a player moved within one world is no worse off than one moved between two.
     */
    private static void awaitChunksAround(Instance instance, Pos spawn, int viewDistance) {
        List<CompletableFuture<Chunk>> pending = new ArrayList<>();
        ChunkRange.chunksInRange(spawn, viewDistance, (chunkX, chunkZ) -> {
            CompletableFuture<Chunk> chunk = instance.loadOptionalChunk(chunkX, chunkZ);
            if (!chunk.isDone()) {
                pending.add(chunk);
            }
        });
        CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).join();
    }
}
