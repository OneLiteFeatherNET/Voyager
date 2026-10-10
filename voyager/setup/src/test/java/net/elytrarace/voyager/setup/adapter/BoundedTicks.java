package net.elytrarace.voyager.setup.adapter;

import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.minestom.server.coordinate.ChunkRange;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.testing.Env;

import java.util.function.BooleanSupplier;

/**
 * Ticks an {@link Env} while a condition holds, at most {@link #MAX_TICKS} times. The bound is a tick count and reads no
 * clock: a slow machine runs the same ticks as a fast one, and a condition that never clears fails the test with a
 * message instead of hanging it. At 20 ticks per second, {@link #MAX_TICKS} is the ten seconds the earlier wall-clock
 * bound allowed.
 */
final class BoundedTicks {

    static final int MAX_TICKS = 200;

    /** Where {@code SetupCommands} puts a builder whose draft has no spawn yet. */
    private static final Pos START = new Pos(0, 64, 0);

    private BoundedTicks() {
    }

    /**
     * Ticks while the condition holds.
     *
     * @throws AssertionError when the condition still holds after {@link #MAX_TICKS} ticks
     */
    static void tickWhile(Env env, BooleanSupplier condition) {
        int ticks = 0;
        while (condition.getAsBoolean()) {
            if (ticks == MAX_TICKS) {
                throw new AssertionError("the condition still held after %d ticks".formatted(MAX_TICKS));
            }
            env.tick();
            ticks++;
        }
    }

    /**
     * Waits until a command has moved the builder into the world of the map it opened.
     *
     * <p>Minestom moves a player into an instance only once the chunks around the spawn are in memory, and it loads
     * them on I/O threads. Ticks alone cannot outlast a slow disk, so this first loads those same chunks on the test
     * thread: {@link Instance#loadChunk} returns the load already in flight, so the join waits for exactly what the
     * move waits for. The ticks that follow then run a move that is already due. The chunk range is the widest
     * Minestom uses, the instance's own view distance plus one, so every chunk the move needs is covered. Nothing here
     * sleeps or reads a clock.
     *
     * @param env the test environment whose ticks run the move
     * @param sessions the builders' open maps
     * @param builder the builder the command was run for
     * @throws AssertionError when the builder has not arrived after {@link #MAX_TICKS} ticks
     */
    static void awaitArrival(Env env, BuilderSessions sessions, Player builder) {
        sessions.find(builder.getUuid()).ifPresent(session -> {
            Instance world = session.instance();
            if (world != builder.getInstance()) {
                Pos arrival = arrivalOf(session.draft());
                ChunkRange.chunksInRange(arrival, world.viewDistance() + 1,
                        (chunkX, chunkZ) -> world.loadChunk(chunkX, chunkZ).join());
            }
        });
        tickWhile(env, () -> sessions.find(builder.getUuid())
                .map(session -> session.instance() != builder.getInstance())
                .orElse(false));
    }

    private static Pos arrivalOf(MapDraft draft) {
        return draft.spawn() == null ? START : Vectors.toMinestom(draft.spawn()).asPos();
    }
}
