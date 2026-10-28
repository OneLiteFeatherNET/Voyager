package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.flight.ArrivalFacing;
import net.elytrarace.voyager.platform.world.Arrivals;
import net.elytrarace.voyager.platform.world.ChunkLoader;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.platform.world.PendingArrival;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;

/**
 * Moves racers onto the next map: the cup's runs are dropped when a map's game phase ends, and each racer is placed on
 * the next map's spawn on their own schedule.
 *
 * <p><strong>This is the operation the rebuild exists for.</strong> The tree being replaced loads map two of a cup and
 * never runs it; E3's state machine is the first that advances, and this is the step that makes the players actually
 * arrive there.
 *
 * <h2>Placing is per racer, and it does not block</h2>
 *
 * <p>A racer is placed on the next map as soon as their own wait has started: when they finished the previous map and
 * their glide-out has ended, or when the previous map's game phase ends. {@link #place} begins that move and returns
 * a {@link PendingArrival}, which the cup ticks until the racer is on the spawn. The tick never waits for a chunk and
 * never waits for the teleport, so the other racers' ticks run as usual while one racer's chunks load.
 *
 * <h2>The hazard: arriving before the ground does</h2>
 *
 * <p>Minestom issue <a href="https://github.com/Minestom/Minestom/issues/2017">#2017</a> — players falling below the
 * map on an instance switch — is closed without a framework fix; the reporter solved it with a chunk check and confirmed
 * teleports. {@link PendingArrival} uses both mechanisms deliberately: it loads every chunk around the spawn before it
 * moves the racer, and the move is a confirmed teleport. A racer moved before the chunks are there has no ground under
 * the client, and falls.
 *
 * <h2>Two maps in one world need no switch at all</h2>
 *
 * <p>{@link MapInstances} caches one instance per world <em>name</em>, because one world backs more than one map. Two
 * maps naming the same world therefore resolve to the same instance, and {@link Player#setInstance(Instance,
 * net.minestom.server.coordinate.Pos)} rejects that outright. {@link PendingArrival} takes the same-world branch in that
 * case, a confirmed teleport within the instance, with the chunks around the spawn loaded first.
 *
 * <h2>A failed load holds the racer and is reported</h2>
 *
 * <p>A chunk that fails to load does not throw into the tick. The racer is held where they stand, the failure is logged
 * once with the world's name, and the placement is retried at the next wait. See {@link PendingArrival}.
 */
public final class MapTransition {

    private final MapInstances instances;
    private final RaceRuns runs;
    private final ChunkLoader chunks;

    /**
     * @param instances the world cache the map's world is resolved through
     * @param runs the per-player run state this transition empties
     */
    public MapTransition(MapInstances instances, RaceRuns runs) {
        this(instances, runs, ChunkLoader.MINESTOM);
    }

    /**
     * @param instances the world cache the map's world is resolved through
     * @param runs the per-player run state this transition empties
     * @param chunks starts each chunk's load and reports its state; production passes {@link ChunkLoader#MINESTOM}
     */
    public MapTransition(MapInstances instances, RaceRuns runs, ChunkLoader chunks) {
        this.instances = instances;
        this.runs = runs;
        this.chunks = chunks;
    }

    /**
     * Begins moving {@code racer} onto {@code map}'s spawn, facing its first ring, and returns without waiting.
     *
     * <p>The chunks around the spawn load in the background. The returned placement is ticked by the cup until the racer
     * is on the spawn; it is not placed when this returns.
     *
     * @param racer the racer to place
     * @param map the course being entered
     * @return the placement, not yet placed
     * @throws net.elytrarace.voyager.platform.world.exception.UnknownWorldException if the map names a world with no
     *     region data behind it
     */
    public PendingArrival place(Player racer, MapDefinition map) {
        Instance target = instances.forWorld(map.world());
        // The spawn is built with the facing of the first ring, so the placement does not turn a racer back to the
        // north the bare position would give them. See ArrivalFacing.
        return PendingArrival.start(racer, map.world(), target, ArrivalFacing.at(map), chunks);
    }

    /**
     * Drops every run, because the map they were over is no longer being played.
     *
     * <p>Called once, when the map's game phase ends, after every racer has been scored. It is not called per racer:
     * a racer placed on the next map while others still race this one must not lose their run.
     */
    public void endMap() {
        runs.clearAll();
    }

    /**
     * Moves {@code player} to {@code target} within the world they already stand in. The arrival step is
     * {@link net.elytrarace.voyager.platform.world.Arrivals#reposition}, shared with the lobby.
     *
     * <p>Public because a course reset moves a racer the same way a map start does.
     */
    public static void reposition(Player player, Pos target) {
        Arrivals.reposition(player, target);
    }
}
