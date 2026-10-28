package net.elytrarace.voyager.platform.world;

import net.minestom.server.coordinate.ChunkRange;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.RelativeFlags;
import net.minestom.server.instance.Instance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * One racer's placement on a spawn, begun on one tick and checked on the ticks after it, so that the server tick never
 * waits for a chunk.
 *
 * <p>{@link #start} asks the loader to start every chunk around the spawn. Each {@link #tick} reads each chunk's
 * {@link ChunkState} and never waits. While a chunk is loading it answers {@link Outcome#WAITING}. When every chunk is
 * present it moves the racer, and on a later tick it answers {@link Outcome#PLACED} once the racer stands on the spawn.
 * The racer stays where they stood until {@code PLACED}.
 *
 * <p>The tick never waits on a future and never calls a method of one: the move's result is not read, and its completion
 * is seen as the racer standing on the spawn. A failure, a chunk that reports {@link ChunkState#FAILED}, or a load or move
 * that is not done within its tick budget ends the placement as {@link Outcome#FAILED}. The failure is logged once with the
 * world's name, and nothing is thrown into the tick. The budget is counted in ticks, so the outcome is repeatable.
 *
 * <p>Each racer has their own placement, so one racer's chunks never hold up another's placement.
 */
public final class PendingArrival {

    private static final Logger LOGGER = LoggerFactory.getLogger(PendingArrival.class);

    /** How many ticks the chunks may load before the placement is taken to have failed: thirty seconds. */
    public static final int LOAD_BUDGET_TICKS = 600;

    /** How many ticks the move may take before the placement is taken to have failed: ten seconds. */
    public static final int MOVE_BUDGET_TICKS = 200;

    /**
     * Squared distance within which the racer counts as standing on the spawn: a tenth of a block, the same tolerance the
     * freeze holds the racer to, so a placement is complete exactly when the freeze can take over.
     */
    private static final double ON_SPOT_SQUARED = 0.01;

    /** Where a placement stands after one tick. */
    public enum Outcome {
        /** Still loading chunks, or the move is in progress. */
        WAITING,
        /** The racer is on the spawn. */
        PLACED,
        /** A chunk failed, or the load or move ran out of time. The racer was not moved and the failure is logged. */
        FAILED
    }

    private enum Phase {
        LOADING, MOVING, PLACED, FAILED
    }

    /** One chunk the placement needs, by its chunk index. */
    private record ChunkIndex(int x, int z) {
    }

    private final Player racer;
    private final String world;
    private final Instance target;
    private final Pos spawn;
    private final ChunkLoader loader;
    private final List<ChunkIndex> chunks;
    private Phase phase = Phase.LOADING;
    private int ticksInPhase;

    private PendingArrival(Player racer, String world, Instance target, Pos spawn, ChunkLoader loader,
            List<ChunkIndex> chunks) {
        this.racer = racer;
        this.world = world;
        this.target = target;
        this.spawn = spawn;
        this.loader = loader;
        this.chunks = chunks;
    }

    /**
     * Begins a placement: asks {@code loader} to start every chunk within the racer's view distance of {@code spawn}, and
     * returns without waiting for any of them.
     *
     * <p>The range is the one the racer's own move uses. Inside the racer's instance it is their effective view distance;
     * into another instance it is their setting capped by the target's, plus one, as {@link Arrivals} does.
     *
     * @param racer the racer to place
     * @param world the name of the world {@code target} holds, for the log line of a failure
     * @param target the instance the racer is placed in; the racer's own instance when it is already there
     * @param spawn where the racer is placed
     * @param loader starts each chunk's load and reports its state
     * @return the placement, not yet placed
     */
    public static PendingArrival start(Player racer, String world, Instance target, Pos spawn, ChunkLoader loader) {
        int viewDistance = racer.getInstance() == target
                ? racer.effectiveViewDistance()
                : Math.min(racer.getSettings().viewDistance(), target.viewDistance()) + 1;
        List<ChunkIndex> chunks = new ArrayList<>();
        ChunkRange.chunksInRange(spawn, viewDistance, (chunkX, chunkZ) -> chunks.add(new ChunkIndex(chunkX, chunkZ)));
        for (ChunkIndex chunk : chunks) {
            loader.start(target, chunk.x(), chunk.z());
        }
        PendingArrival arrival = new PendingArrival(racer, world, target, spawn, loader, chunks);
        // Chunks that are already present need no tick of waiting: the move begins now, and the next tick sees the racer
        // on the spawn. A chunk still loading is left to the ticks that follow.
        arrival.loading();
        return arrival;
    }

    /**
     * Whether the move has begun. A racer whose move has begun is not held in place by the wait, because the move is
     * taking them to the spawn.
     *
     * @return {@code true} from the tick the move is started until the placement ends
     */
    public boolean isMoving() {
        return phase == Phase.MOVING || phase == Phase.PLACED;
    }

    /**
     * Advances the placement by one tick. Never waits.
     *
     * <p>The tick that finds every chunk present starts the move. The placement is reported {@link Outcome#PLACED} on a
     * later tick, once the racer stands on the spawn.
     *
     * @return where the placement stands after this tick
     */
    public Outcome tick() {
        return switch (phase) {
            case LOADING -> loading();
            case MOVING -> moving();
            case PLACED -> Outcome.PLACED;
            case FAILED -> Outcome.FAILED;
        };
    }

    private Outcome loading() {
        boolean allPresent = true;
        for (ChunkIndex chunk : chunks) {
            ChunkState state = loader.state(target, chunk.x(), chunk.z());
            if (state == ChunkState.FAILED) {
                return fail("chunk %d,%d could not be loaded".formatted(chunk.x(), chunk.z()));
            }
            allPresent &= state == ChunkState.PRESENT;
        }
        if (!allPresent) {
            if (++ticksInPhase > LOAD_BUDGET_TICKS) {
                return fail("its chunks were not all loaded within %d ticks".formatted(LOAD_BUDGET_TICKS));
            }
            return Outcome.WAITING;
        }
        if (racer.getInstance() == target) {
            racer.teleport(spawn, null, RelativeFlags.NONE, true);
        } else {
            racer.setInstance(target, spawn);
        }
        phase = Phase.MOVING;
        ticksInPhase = 0;
        return Outcome.WAITING;
    }

    private Outcome moving() {
        if (racer.getInstance() == target && racer.getPosition().distanceSquared(spawn) <= ON_SPOT_SQUARED) {
            phase = Phase.PLACED;
            return Outcome.PLACED;
        }
        if (++ticksInPhase > MOVE_BUDGET_TICKS) {
            return fail("the move did not complete within %d ticks".formatted(MOVE_BUDGET_TICKS));
        }
        return Outcome.WAITING;
    }

    /** Records the failure once, and logs it with the world's name. Nothing is thrown into the tick. */
    private Outcome fail(String reason) {
        phase = Phase.FAILED;
        LOGGER.warn("Racer {} was not placed on world '{}': {}; the racer is held where they are and the placement is "
                + "retried at the next wait", racer.getUsername(), world, reason);
        return Outcome.FAILED;
    }
}
