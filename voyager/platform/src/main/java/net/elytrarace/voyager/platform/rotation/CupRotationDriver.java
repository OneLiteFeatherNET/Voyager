package net.elytrarace.voyager.platform.rotation;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.catalog.LoadedCatalog;
import net.elytrarace.voyager.platform.catalog.WorldOpener;
import net.elytrarace.voyager.race.rotation.CupRotation;
import net.elytrarace.voyager.race.rotation.CupRotationStore;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Arms the next cup of the rotation when a cup's results have lingered and its racers have been handed away, and keeps
 * the worlds in heap to that cup's worlds.
 *
 * <p><strong>What arming does.</strong> It advances the rotation past every cup that cannot be played, offers the next
 * cup's catalogue to the holder (so the next round pins it), writes the name to the store, and then swaps the worlds: the
 * worlds the previous cup needed and the armed cup does not are released, and the armed cup's missing worlds are opened.
 * Released before opened, so one cup's worlds, with the lobby, are in heap at a time. A world that both cups need is kept.
 *
 * <p><strong>A release waits.</strong> An instance refuses to close with a racer in it, and the handed-away racers stay
 * connected until the hub moves them. So a swap that would release a world with a player in it is deferred, and
 * {@link #tick()} tries it again on each server tick until the world is empty.
 *
 * <p><strong>A failed open falls back.</strong> A world of the armed cup that cannot be opened is logged once as an error,
 * the cup is marked unplayable for the rest of the JVM, and the rotation moves on to the next cup that opens. When none
 * does, the cup that was just played is reopened and stays armed.
 *
 * <p>Without a rotation ({@link #single}) every method is a no-op and the holder plays the catalogue it was given, as before.
 * The driver is only ever called from the server tick, so it carries no lock.
 */
public final class CupRotationDriver {

    private static final Logger LOGGER = LoggerFactory.getLogger(CupRotationDriver.class);

    /** The worlds of a driver without a rotation. It is never asked to open one, so it refuses if it ever is. */
    private static final WorldOpener NO_WORLDS = new WorldOpener() {
        @Override
        public boolean holdsRegionData(String world) {
            throw new IllegalStateException("a server without a cup rotation opens no world");
        }

        @Override
        public boolean isOpen(String world) {
            return false;
        }

        @Override
        public void open(String world) {
            throw new IllegalStateException("a server without a cup rotation opens no world");
        }

        @Override
        public void discard(String world) {
            // Nothing is ever opened, so nothing is discarded.
        }

        @Override
        public boolean hasPlayers(String world) {
            return false;
        }

        @Override
        public boolean regionDataChanged(String world) {
            return false;
        }
    };

    private final CatalogHolder holder;
    private final WorldOpener worlds;
    private final CupRotationStore store;
    private final Clock clock;
    /** The rotation, or {@code null} for a server without one. Moves only when a cup is armed. */
    private @Nullable CupRotation rotation;
    /** Cups that are not played for the rest of this JVM: skipped at boot, or whose worlds would not open. */
    private final Set<String> unplayable = new HashSet<>();
    /** The cup the next round plays, once a rotation has armed one. Before that, the boot cup. */
    private LoadedCatalog upcoming;
    /** The worlds the played cup needs and this driver has opened or kept open. */
    private final Set<String> openWorlds = new LinkedHashSet<>();
    /** Whether the worlds of {@link #upcoming} still have to be swapped in. */
    private boolean swapPending;

    /**
     * @param holder  the catalogue holder; the boot catalogue is the one it holds, and an armed cup is offered to it
     * @param worlds  opens and releases the worlds the cups name
     * @param store   keeps the armed cup's name across a restart
     * @param clock   stamps each armed catalogue
     * @param plan    the boot plan; its rotation is empty for a server without one
     * @throws IllegalArgumentException if the boot cup of the plan is not the cup the holder holds
     */
    public CupRotationDriver(CatalogHolder holder, WorldOpener worlds, CupRotationStore store, Clock clock,
            RotationPlan plan) {
        this.holder = Objects.requireNonNull(holder, "holder must not be null");
        this.worlds = Objects.requireNonNull(worlds, "worlds must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.upcoming = holder.current();
        this.unplayable.addAll(plan.skipped());
        this.rotation = plan.rotation().isEmpty()
                ? null
                : CupRotation.starting(plan.rotation(), upcoming.cup().key().asString());
        this.openWorlds.addAll(worldsOf(upcoming));
    }

    /** A driver for a server without a rotation: it arms nothing, moves no world, and plays the holder's catalogue. */
    public static CupRotationDriver single(CatalogHolder holder) {
        return new CupRotationDriver(holder, NO_WORLDS, CupRotationStore.NONE, Clock.systemUTC(),
                RotationPlan.single(Optional.empty()));
    }

    /**
     * The catalogue the next round plays, and the one the lobby shows: the armed cup of a rotation, or the holder's
     * catalogue without one. A pending reload does not change it until a round promotes it.
     */
    public LoadedCatalog upcoming() {
        return rotation == null ? holder.current() : upcoming;
    }

    /** Whether more than one cup takes part, so the lobby and the state file name the armed cup. */
    public boolean rotates() {
        return rotation != null && rotation.rotates();
    }

    /**
     * The name a reload resolves the cup by: the armed cup of a rotation, in its canonical form, or empty without one.
     */
    public Optional<String> armedName() {
        return rotation == null ? Optional.empty() : Optional.of(upcoming.cup().key().asString());
    }

    /**
     * Arms the next cup of the rotation. Called once, when a cup has finished, its results have lingered and its racers
     * have been handed away. A no-op without a rotation.
     */
    public void armNext() {
        if (rotation == null) {
            return;
        }
        if (!advanceToPlayable()) {
            LOGGER.error("no cup of the rotation can be armed; '{}' stays armed", upcoming.cup().key().asString());
            return;
        }
        LOGGER.info("Cup '{}' is armed for the next round", upcoming.cup().key().asString());
        tick();
    }

    /**
     * Swaps the worlds of the armed cup in, when a swap is pending and no racer stands in a world that would be released.
     * Called once per server tick. A no-op when nothing is pending.
     */
    public void tick() {
        if (rotation == null || !swapPending) {
            return;
        }
        boolean restoring = false;
        while (swapPending) {
            Outcome outcome = swap(worldsOf(upcoming));
            if (outcome == Outcome.DEFERRED) {
                return;
            }
            if (outcome == Outcome.DONE) {
                swapPending = false;
                return;
            }
            if (restoring) {
                swapPending = false;
                LOGGER.error("the cup '{}' that was just played cannot be reopened either; the server keeps it running as far as "
                        + "its worlds allow", upcoming.cup().key().asString());
                return;
            }
            unplayable.add(upcoming.cup().key().asString());
            if (!advanceToPlayable()) {
                restore();
                restoring = true;
            }
        }
    }

    /**
     * Moves the rotation to the next cup that is playable and offers its catalogue. Returns false when no cup is left.
     * Nothing moves when it returns false.
     */
    private boolean advanceToPlayable() {
        CupRotation current = Objects.requireNonNull(rotation);
        LoadedCatalog freshest = holder.pending().orElseGet(holder::current);
        CupRotation candidate = current;
        for (int step = 0; step < current.cups().size(); step++) {
            candidate = candidate.advance();
            String name = candidate.armed();
            if (unplayable.contains(name)) {
                continue;
            }
            Optional<CupDefinition> cup = freshest.snapshot().cupByName(name);
            if (cup.isEmpty()) {
                unplayable.add(name);
                LOGGER.warn("cup '{}' of the rotation is not in the catalogue; the rotation passes over it", name);
                continue;
            }
            rotation = candidate;
            upcoming = new LoadedCatalog(freshest.snapshot(), cup.get(), clock.instant());
            holder.offer(upcoming);
            store.write(name);
            swapPending = true;
            return true;
        }
        return false;
    }

    /** Puts the cup that was just played back as the armed cup, so its worlds are reopened and the next round plays it. */
    private void restore() {
        LoadedCatalog played = holder.current();
        String name = played.cup().key().asString();
        Objects.requireNonNull(rotation);
        if (rotation.cups().contains(name)) {
            rotation = CupRotation.starting(rotation.cups(), name);
        }
        upcoming = played;
        holder.offer(played);
        store.write(name);
        swapPending = true;
        LOGGER.warn("no other cup of the rotation can be opened; '{}' stays armed", name);
    }

    /**
     * Releases the worlds the played cup needs and the armed cup does not, then opens the armed cup's missing worlds. Each
     * world that needs to close with a racer in it defers the whole swap; nothing is released before the check passes.
     */
    private Outcome swap(Set<String> needed) {
        Set<String> released = new LinkedHashSet<>(openWorlds);
        released.removeAll(needed);
        for (String world : released) {
            if (worlds.hasPlayers(world)) {
                return Outcome.DEFERRED;
            }
        }
        for (String world : released) {
            try {
                worlds.discard(world);
            } catch (RuntimeException failure) {
                LOGGER.warn("world '{}' did not close cleanly after its cup ended: {}", world, failure.getMessage());
            }
        }
        openWorlds.removeAll(released);

        List<String> opened = new ArrayList<>();
        for (String world : needed) {
            if (worlds.isOpen(world)) {
                continue;
            }
            try {
                worlds.open(world);
                opened.add(world);
            } catch (RuntimeException failure) {
                for (String done : opened) {
                    discardQuietly(done);
                }
                LOGGER.error("world '{}' of cup '{}' could not be opened: {}", world,
                        upcoming.cup().key().asString(), failure.getMessage(), failure);
                return Outcome.FAILED;
            }
        }
        openWorlds.clear();
        openWorlds.addAll(needed);
        LOGGER.info("Worlds for cup '{}': released {}, opened {}", upcoming.cup().key().asString(), released, opened);
        return Outcome.DONE;
    }

    private void discardQuietly(String world) {
        try {
            worlds.discard(world);
        } catch (RuntimeException failure) {
            LOGGER.warn("world '{}' did not close after a failed arm: {}", world, failure.getMessage());
        }
    }

    /** The distinct worlds a catalogue's cup plays, in map order. */
    private static Set<String> worldsOf(LoadedCatalog catalog) {
        Set<String> names = new LinkedHashSet<>();
        for (String mapName : catalog.cup().mapNames()) {
            catalog.snapshot().mapByName(mapName).map(MapDefinition::world).ifPresent(names::add);
        }
        return names;
    }

    private enum Outcome {
        /** The worlds are swapped. */
        DONE,
        /** A world to release still has a racer in it; the swap is tried again on the next tick. */
        DEFERRED,
        /** A world the armed cup needs would not open. */
        FAILED
    }
}
