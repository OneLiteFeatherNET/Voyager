package net.elytrarace.server.ecs.component;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.server.cup.BoostConfig;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks firework boost intent, sustained-burn state, and cooldown for a player entity.
 * <p>
 * Thread model:
 * <ul>
 *   <li>{@link #requestBoost()} — called from the Netty I/O thread; uses {@link AtomicBoolean}.</li>
 *   <li>All other methods — called exclusively from the tick thread by {@link FireworkBoostSystem}.</li>
 *   <li>{@link #boostConfig} — {@code volatile} so map-load pushes from any thread are visible.</li>
 * </ul>
 * <p>
 * Cooldown enforcement uses wall-clock time ({@link System#currentTimeMillis()}) rather than tick
 * counting, so server-side lag cannot desync the cooldown from the client-side item cooldown visual
 * sent via {@code SetCooldownPacket}.
 */
public class FireworkBoostComponent implements Component {

    private final AtomicBoolean boostRequested = new AtomicBoolean(false);
    /** Absolute wall-clock deadline in ms; 0 means no active cooldown. */
    private long cooldownExpiryMs = 0L;
    /** Remaining ticks of sustained post-kick thrust; 0 means not burning. */
    private int burnTicksRemaining = 0;
    private volatile BoostConfig boostConfig = BoostConfig.DEFAULT;

    // -------------------------------------------------------------------------
    // Boost request (cross-thread)
    // -------------------------------------------------------------------------

    /** Signals that the player pressed the boost button. Safe to call from any thread. */
    public void requestBoost() {
        boostRequested.set(true);
    }

    /**
     * Atomically reads and clears the boost-request flag.
     * Returns {@code true} if a boost was pending (and is now consumed).
     */
    public boolean claimBoostRequest() {
        return boostRequested.compareAndSet(true, false);
    }

    // -------------------------------------------------------------------------
    // Cooldown (wall-clock, tick-thread)
    // -------------------------------------------------------------------------

    /** Returns {@code true} while the wall-clock cooldown has not yet expired. */
    public boolean isOnCooldown() {
        return System.currentTimeMillis() < cooldownExpiryMs;
    }

    /**
     * Starts the cooldown by recording an expiry deadline ({@code now + cooldownMs}).
     * Call immediately after activating a boost.
     */
    public void startCooldown() {
        cooldownExpiryMs = System.currentTimeMillis() + boostConfig.cooldownMs();
    }

    /**
     * Returns the remaining cooldown as a tick count (floor division by 50 ms), for use in
     * {@code SetCooldownPacket}. Returns 0 when the cooldown has expired.
     */
    public int getCooldownRemainingTicks() {
        long remainingMs = cooldownExpiryMs - System.currentTimeMillis();
        return (int) Math.max(0L, remainingMs / 50L);
    }

    // -------------------------------------------------------------------------
    // Sustained burn (tick-thread)
    // -------------------------------------------------------------------------

    /** Returns {@code true} while sustained thrust is still being applied. */
    public boolean isBurning() {
        return burnTicksRemaining > 0;
    }

    /** Starts a burn of the configured duration. Call once at boost activation. */
    public void startBurn() {
        burnTicksRemaining = boostConfig.burnDurationTicks();
    }

    /** Decrements the burn counter by one tick. No-op when already at zero. */
    public void tickBurn() {
        if (burnTicksRemaining > 0) {
            burnTicksRemaining--;
        }
    }

    /** Returns the remaining burn ticks (used for falloff calculation). */
    public int getBurnTicksRemaining() {
        return burnTicksRemaining;
    }

    /** Cancels any active burn immediately (e.g. on teleport-to-start or player leave). */
    public void cancelBurn() {
        burnTicksRemaining = 0;
    }

    // -------------------------------------------------------------------------
    // Config (volatile, any thread)
    // -------------------------------------------------------------------------

    public BoostConfig getBoostConfig() {
        return boostConfig;
    }

    /**
     * Updates the boost configuration (e.g. when a new map loads with different boost feel).
     * Safe to call from any thread.
     */
    public void setBoostConfig(BoostConfig boostConfig) {
        this.boostConfig = boostConfig;
    }
}
