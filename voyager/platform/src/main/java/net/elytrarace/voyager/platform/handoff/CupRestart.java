package net.elytrarace.voyager.platform.handoff;

import net.elytrarace.voyager.race.rotation.CupRotationStore;

import org.jetbrains.annotations.NotNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * The restart after a finished cup ({@code VOYAGER_RESTART_AFTER_CUP}, ADR-0047): the state machine that decides when the
 * service ends its process, so that CloudNet starts a fresh one.
 *
 * <p>The room asks for the restart once a finished cup's racers have been handed to the hub and the next cup has been armed
 * ({@link #request}). With a rotation of more than one cup the request first reads the state back, and it is accepted only
 * when the state holds the next cup; a state that does not hold it refuses the restart and the server keeps running, which
 * is what it does without the setting. An accepted request then waits, one tick at a time ({@link #tick}), until no player is
 * connected or {@link #DRAIN_TICKS} ticks have passed, and then asks for the stop once. The stop itself is the composition
 * root's clean shutdown; this class only says when.
 *
 * <p>No clock and no Minestom type: the drain is counted in ticks, as the linger is. Every method runs on the server tick.
 */
@NotNullByDefault
public final class CupRestart {

    private static final Logger LOGGER = LoggerFactory.getLogger(CupRestart.class);

    /**
     * How many ticks the service waits for the handed-off racers to leave before it stops anyway: 100 ticks, which is 5 s at 20 TPS.
     * A constant, not a setting. The hub move is asynchronous, so a stop straight after it could show the racers the stop's kick.
     */
    public static final int DRAIN_TICKS = 100;

    private final boolean enabled;
    private final CupRotationStore store;
    /** Names the state in the one ERROR, so an operator can find the file. */
    private final String stateName;
    private boolean requested;
    private boolean stopAsked;
    private int ticksSinceRequest;

    private CupRestart(boolean enabled, CupRotationStore store, String stateName) {
        this.enabled = enabled;
        this.store = store;
        this.stateName = stateName;
    }

    /** The restart of a server without the setting: it is never requested and never asks for a stop. */
    public static CupRestart disabled() {
        return new CupRestart(false, CupRotationStore.NONE, "");
    }

    /**
     * A restart that reads the rotation state back before it is accepted.
     *
     * @param store     the store the rotation writes the armed cup to
     * @param stateName what the state is called in a message, such as the state file's path
     */
    public static CupRestart after(CupRotationStore store, String stateName) {
        return new CupRestart(true, store, stateName);
    }

    /** Whether the setting is on, so the room may ask for a restart at all. */
    public boolean enabled() {
        return enabled;
    }

    /** Whether a restart has been accepted. Once it has, no cup may start: the room holds its joiners for the hand-off. */
    public boolean requested() {
        return requested;
    }

    /**
     * Asks for the restart after a finished cup. Accepted once; a later request, or a request while disabled, changes nothing.
     *
     * @param finishedCup the cup that finished
     * @param nextCup     the cup the rotation has armed for the next round
     * @param keepsState  whether the rotation has more than one cup, so the state must hold {@code nextCup} to be accepted
     * @return whether this call accepted the restart
     */
    public boolean request(String finishedCup, String nextCup, boolean keepsState) {
        if (!enabled || requested) {
            return false;
        }
        if (keepsState && !store.read().equals(Optional.of(nextCup))) {
            LOGGER.error("the cup rotation state {} does not hold '{}' after the cup '{}' finished; the service keeps running "
                    + "and does not restart", stateName, nextCup, finishedCup);
            return false;
        }
        requested = true;
        LOGGER.info("Restarting the service: cup '{}' finished and its racers are with the hub, so the next service begins "
                + "fresh and plays '{}'", finishedCup, nextCup);
        return true;
    }

    /**
     * One server tick of the drain. Returns true exactly once: when no player is connected any more, or when the drain limit has
     * passed with someone still connected (one WARN names how many). Always false before a request, and after the stop is asked.
     *
     * @param connected the number of players still connected
     * @return whether the stop should be asked for now
     */
    public boolean tick(int connected) {
        if (!requested || stopAsked) {
            return false;
        }
        ticksSinceRequest++;
        if (connected == 0) {
            stopAsked = true;
            return true;
        }
        if (ticksSinceRequest >= DRAIN_TICKS) {
            LOGGER.warn("{} player(s) still connected {} ticks after the cup's racers were handed to the hub; the stop disconnects "
                    + "them", connected, DRAIN_TICKS);
            stopAsked = true;
            return true;
        }
        return false;
    }
}
