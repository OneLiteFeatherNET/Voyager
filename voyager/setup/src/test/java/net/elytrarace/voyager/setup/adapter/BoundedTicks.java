package net.elytrarace.voyager.setup.adapter;

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
}
