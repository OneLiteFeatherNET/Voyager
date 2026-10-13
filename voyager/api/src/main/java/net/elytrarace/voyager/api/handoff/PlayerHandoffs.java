package net.elytrarace.voyager.api.handoff;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The slot that holds the installed {@link PlayerHandoff}. The bridge installs one when its extension starts, and the
 * server reads it when a cup's results have lingered.
 *
 * <p>There is one slot, {@link #SLOT}, and it is process-wide: the extension loader delegates to the application class loader
 * first (ADR-0033), so the server and the extension see the same constant. It is an enum because the api module's rule
 * allows only records, interfaces and enums among its top-level types, and a single-constant enum is the one holder shape
 * that is also a singleton. A later install replaces the earlier one, and {@link #uninstall()} clears it, which is how a test
 * leaves the slot empty.
 */
public enum PlayerHandoffs {

    /** The one slot. */
    SLOT;

    private final AtomicReference<PlayerHandoff> held = new AtomicReference<>();

    /**
     * Installs {@code handoff}, replacing any handoff installed before it.
     *
     * @param handoff the handoff to hold
     */
    public void install(PlayerHandoff handoff) {
        held.set(Objects.requireNonNull(handoff, "handoff must not be null"));
    }

    /** Clears the slot, so that {@link #current()} answers empty. */
    public void uninstall() {
        held.set(null);
    }

    /**
     * @return the installed handoff, or empty when none is installed
     */
    public Optional<PlayerHandoff> current() {
        return Optional.ofNullable(held.get());
    }
}
