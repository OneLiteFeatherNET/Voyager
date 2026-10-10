package net.elytrarace.voyager.platform.flight;

import net.elytrarace.voyager.api.race.BoostConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Whose firework is burning, for how much longer, and who may not light another one yet.
 *
 * <p>This is the whole of the boost's determinism. Vanilla's rocket decides its own lifetime with
 * two dice rolls, which is fine for a fireworks display and not for a race; here the server counts
 * the ticks and the rocket entity is only the thing the client watches. Nothing in this class is
 * random, nothing in it reads a clock, and nothing in it knows what a rocket or a player is — it
 * holds two counters per player and is driven by {@link #advance()}.
 *
 * <h2>The two counters, and why the cooldown starts with the burn</h2>
 *
 * <p>A boost sets both at once: the burn to {@link BoostConfig#burnDurationTicks()} and the cooldown
 * to {@link BoostConfig#cooldownTicks()}. {@link #advance()} decrements both. So the cooldown is
 * measured <strong>from the tick the burn started</strong>, and because {@code BoostConfig} refuses a
 * cooldown that is not strictly longer than the burn, a second boost cannot begin before the first
 * has ended. That is what makes "two rockets on one player" a case this class cannot produce rather
 * than one downstream has to handle — see {@code BoostConfig}'s javadoc for why the simulation's
 * boolean input cannot express it.
 *
 * <p>The configuration is read at the moment a boost is requested and then not consulted again. A
 * burn started under one map's tuning therefore runs that map's length even if the map changes under
 * it, which is the only answer that cannot produce a burn of some third length nobody configured.
 *
 * <h2>Where {@link #advance()} goes in a tick</h2>
 *
 * <p><strong>After the tick's flight sample has been taken, not before.</strong> The sampler reports
 * {@link #burning(UUID)} and {@link #ticksRemaining(UUID)} for this tick; advancing first would spend
 * a tick of the burn before anything had observed it, and a boost configured for 30 ticks would drive
 * 29 of them. The count is off by one in a way no assertion downstream can see, because every value
 * it produces stays self-consistent — the same shape of defect the E2a trace recorder's off-by-one
 * tick was. {@code CupSession.tick()} is where the order is written down.
 *
 * <p>Not thread-safe, like the {@link FlightTracker} beside it: every method must be called from the
 * single thread driving the tick loop.
 */
public final class FireworkBoostTracker {

    private final Map<UUID, Burn> burnByPlayer = new HashMap<>();

    /**
     * Lights a rocket for {@code playerId}, if they are allowed one.
     *
     * <p>Refused while a cooldown is running — which, by the paragraph above, includes the whole of
     * the current burn — and refused for a player who is not gliding. The second check is not
     * politeness: Vanilla's rocket applies its impulse only to an entity that is fall-flying, so a
     * rocket used on the ground moves nobody, and a server that started a burn for it would report a
     * boost to the simulation that the client never felt, and then hold the racer in a cooldown for a
     * boost they never got.
     *
     * @param playerId who asked
     * @param config the tuning of the map being raced, read now and not again for this burn
     * @param flyingWithElytra this tick's gliding flag for {@code playerId}
     * @return whether a burn started; {@code false} means nothing changed
     */
    public boolean requestBoost(UUID playerId, BoostConfig config, boolean flyingWithElytra) {
        if (!flyingWithElytra) {
            return false;
        }
        Burn held = burnByPlayer.get(playerId);
        if (held != null && held.cooldownTicks > 0) {
            return false;
        }
        burnByPlayer.put(playerId, new Burn(config.burnDurationTicks(), config.cooldownTicks()));
        return true;
    }

    /**
     * Counts every burn and every cooldown down by one tick. Call once per server tick, after the
     * tick's flight sample has been taken.
     */
    public void advance() {
        burnByPlayer.values().removeIf(Burn::expireOneTick);
    }

    /** Whether a rocket is burning for {@code playerId} on this tick. */
    public boolean burning(UUID playerId) {
        return ticksRemaining(playerId) > 0;
    }

    /**
     * How many ticks of burn are left for {@code playerId}, this tick included; zero when none is
     * running.
     */
    public int ticksRemaining(UUID playerId) {
        Burn held = burnByPlayer.get(playerId);
        return held == null ? 0 : held.burnTicks;
    }

    /**
     * How many ticks before {@code playerId} may boost again; zero when they may boost now. Non-zero
     * for the whole of a burn as well as for the wait after it, because the cooldown is measured from
     * the burn's start.
     */
    public int cooldownTicksRemaining(UUID playerId) {
        Burn held = burnByPlayer.get(playerId);
        return held == null ? 0 : held.cooldownTicks;
    }

    /**
     * Drops everything held for {@code playerId} — call this when a player disconnects, and when a
     * map ends under them. A racer who left mid-burn and came back would otherwise reconnect into the
     * remains of a cooldown they cannot see the reason for, and a burn started on one map would carry
     * into the launch of the next.
     *
     * <p>Unlike {@link FlightTracker#forget(UUID)} this needs no package-private seam to prove it
     * happened: {@link #burning(UUID)} and {@link #cooldownTicksRemaining(UUID)} both answer zero
     * afterwards, and an entry whose counters have both run out is dropped by {@link #advance()}
     * anyway, so there is no state left over that only a wider surface could see.
     */
    public void forget(UUID playerId) {
        burnByPlayer.remove(playerId);
    }

    /**
     * Ends the burn running for {@code playerId}, if one is, and leaves its cooldown running.
     *
     * <p>Used by a course reset. {@link #forget(UUID)} is the wrong tool there: it drops the cooldown too, and
     * a racer who could refresh a rocket by landing would boost again sooner than the map allows.
     */
    public void cancelBurn(UUID playerId) {
        Burn held = burnByPlayer.get(playerId);
        if (held != null) {
            held.burnTicks = 0;
        }
    }

    /** Drops everything held for everybody — a cup restarting owes nobody the last one's cooldown. */
    public void clear() {
        burnByPlayer.clear();
    }

    /**
     * One player's two counters. Mutable and package-private by design: this is on the tick path for
     * every racer, and a record replaced in the map twice a tick would allocate for nothing — the
     * exception java-style §2 names, taken here because the object never leaves this class.
     */
    private static final class Burn {

        private int burnTicks;
        private int cooldownTicks;

        private Burn(int burnTicks, int cooldownTicks) {
            this.burnTicks = burnTicks;
            this.cooldownTicks = cooldownTicks;
        }

        /**
         * Counts both down by one and answers whether this player is worth holding an entry for any
         * longer.
         *
         * @return true once neither counter is running, so the entry can be dropped
         */
        private boolean expireOneTick() {
            if (burnTicks > 0) {
                burnTicks--;
            }
            if (cooldownTicks > 0) {
                cooldownTicks--;
            }
            return burnTicks == 0 && cooldownTicks == 0;
        }
    }
}
