package net.elytrarace.server.ecs.component;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.server.cup.BoostConfig;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks firework boost intent and cooldown for a player entity.
 * <p>
 * {@link #requestBoost()} is called from the Netty I/O thread when the player
 * uses a firework rocket. {@link FireworkBoostSystem} reads and clears the flag
 * on the tick thread each game tick, so all {@code volatile}/{@code AtomicBoolean}
 * fields here are intentionally thread-safe.
 * <p>
 * Cooldown enforcement uses wall-clock time ({@link System#currentTimeMillis()})
 * rather than tick counting. This keeps the server-side guard in sync with the
 * client-side item cooldown visual (sent via {@code SetCooldownPacket}): both
 * measure real elapsed time, so server lag cannot cause them to diverge.
 */
public class FireworkBoostComponent implements Component {

    private final AtomicBoolean boostRequested = new AtomicBoolean(false);
    /** Absolute wall-clock deadline in ms. Zero means no active cooldown. */
    private long cooldownExpiryMs = 0L;
    private volatile BoostConfig boostConfig = BoostConfig.DEFAULT;

    /**
     * Signals that the player pressed the boost button.
     * Safe to call from any thread.
     */
    public void requestBoost() {
        boostRequested.set(true);
    }

    /**
     * Atomically reads and clears the boost-request flag.
     * Returns {@code true} if a boost was pending (and now consumed).
     */
    public boolean claimBoostRequest() {
        return boostRequested.compareAndSet(true, false);
    }

    /**
     * Returns {@code true} while the wall-clock cooldown has not yet expired.
     * Safe to call from any thread.
     */
    public boolean isOnCooldown() {
        return System.currentTimeMillis() < cooldownExpiryMs;
    }

    /**
     * Starts the cooldown by recording an expiry timestamp based on the current
     * wall-clock time plus the configured duration. Call immediately after applying a boost.
     */
    public void startCooldown() {
        cooldownExpiryMs = System.currentTimeMillis() + boostConfig.cooldownMs();
    }

    /**
     * Returns the remaining cooldown in server ticks (rounded down), for use in
     * {@code SetCooldownPacket}. Returns 0 when the cooldown has expired.
     */
    public int getCooldownRemainingTicks() {
        long remainingMs = cooldownExpiryMs - System.currentTimeMillis();
        return (int) Math.max(0L, remainingMs / 50L);
    }

    public BoostConfig getBoostConfig() {
        return boostConfig;
    }

    /**
     * Updates the boost configuration, e.g. when a new map loads with different boost feel.
     * Safe to call from any thread.
     */
    public void setBoostConfig(BoostConfig boostConfig) {
        this.boostConfig = boostConfig;
    }
}
