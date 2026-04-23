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
 */
public class FireworkBoostComponent implements Component {

    private final AtomicBoolean boostRequested = new AtomicBoolean(false);
    private int cooldownRemainingTicks = 0;
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
     * Decrements the cooldown counter by one tick. No-op when already at zero.
     * Called by {@link FireworkBoostSystem} every tick.
     */
    public void tickCooldown() {
        if (cooldownRemainingTicks > 0) {
            cooldownRemainingTicks--;
        }
    }

    /** Returns {@code true} while the cooldown has not yet expired. */
    public boolean isOnCooldown() {
        return cooldownRemainingTicks > 0;
    }

    /**
     * Resets the cooldown to the full duration defined in {@link #getBoostConfig()}.
     * Call this immediately after applying a boost.
     */
    public void startCooldown() {
        cooldownRemainingTicks = (int) (boostConfig.cooldownMs() / 50L);
    }

    public int getCooldownRemainingTicks() {
        return cooldownRemainingTicks;
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
