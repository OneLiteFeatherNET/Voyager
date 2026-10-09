package net.elytrarace.voyager.platform.catalog;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The catalogue the server plays, and at most one replacement waiting for the next round.
 *
 * <p>Two references, one swap. {@link #current()} is what a round pins when it starts. {@link #offer} puts a
 * candidate in the pending slot without touching what rounds read. {@link #promoteForNewRound()} is the only
 * place the current one changes, and it is called once per round start, so a reader never sees a catalogue
 * that is half old and half new.
 *
 * <p>Holds no port and carries no DI annotation: the composition root constructs it with the boot catalogue,
 * and the container holds this one object whose content changes.
 */
public final class CatalogHolder {

    private final AtomicReference<LoadedCatalog> current;
    private final AtomicReference<LoadedCatalog> pending = new AtomicReference<>();

    /** @param initial the catalogue the server boots with; the first round plays it unless one is offered first */
    public CatalogHolder(LoadedCatalog initial) {
        this.current = new AtomicReference<>(Objects.requireNonNull(initial, "initial must not be null"));
    }

    /** The catalogue a round starting now would pin, or the one a running round pinned. */
    public LoadedCatalog current() {
        return current.get();
    }

    /**
     * Offers a replacement. The last valid offer before a round starts is the one that plays; an offer does
     * not change {@link #current()}.
     */
    public void offer(LoadedCatalog candidate) {
        pending.set(Objects.requireNonNull(candidate, "candidate must not be null"));
    }

    /** The replacement waiting for the next round, if any. */
    public Optional<LoadedCatalog> pending() {
        return Optional.ofNullable(pending.get());
    }

    /**
     * Makes the pending catalogue current, if there is one, and returns the catalogue a round starting now
     * pins. Called once per round start and by nothing else.
     *
     * <p>{@code getAndSet(null)} means an offer that lands during this call is either taken by this round or
     * kept for the next, and is never lost.
     */
    public LoadedCatalog promoteForNewRound() {
        LoadedCatalog next = pending.getAndSet(null);
        if (next != null) {
            current.set(next);
        }
        return current.get();
    }
}
