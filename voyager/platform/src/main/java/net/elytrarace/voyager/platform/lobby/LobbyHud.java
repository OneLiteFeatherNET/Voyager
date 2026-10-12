package net.elytrarace.voyager.platform.lobby;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The boss bar a waiting racer reads in the lobby: how many racers are online and needed, or how long the start has.
 *
 * <p>One bar per racer, as {@code RaceHud} keeps one per racer during a race. The bar is shown only while the racer
 * stands in the lobby instance, so the race bar takes over when the racer enters a map, with no coordination between
 * the two. It is sent only when its text or its progress changes, not every tick, following the refresh pattern of
 * {@code RaceHud}.
 */
@NotNullByDefault
public final class LobbyHud {

    private static final BossBar.Color COLOUR = BossBar.Color.WHITE;
    private static final BossBar.Overlay OVERLAY = BossBar.Overlay.PROGRESS;

    /** What a racer's bar currently says, so an unchanged state is recognised without a packet. */
    private record Shown(BossBar bar, Component title, float progress) {
    }

    private final Map<UUID, Shown> bars = new HashMap<>();

    /**
     * Shows the lobby bar to {@code racer} with {@code title} and {@code progress}, if the racer stands in {@code lobby},
     * and hides it if not.
     *
     * @param racer    the racer the bar is for
     * @param lobby    the lobby instance; the bar shows only while the racer is in it
     * @param title    the line the bar reads
     * @param progress the fraction of the lobby that is left, from zero to one
     * @return whether what the racer sees changed, so a caller can count the updates it sent
     */
    public boolean update(Player racer, Instance lobby, Component title, float progress) {
        if (racer.getInstance() != lobby) {
            return hide(racer);
        }
        UUID id = racer.getUuid();
        Shown current = bars.get(id);
        if (current == null) {
            BossBar created = BossBar.bossBar(title, progress, COLOUR, OVERLAY);
            racer.showBossBar(created);
            bars.put(id, new Shown(created, title, progress));
            return true;
        }
        if (current.title().equals(title) && Float.compare(current.progress(), progress) == 0) {
            return false;
        }
        current.bar().name(title);
        current.bar().progress(progress);
        bars.put(id, new Shown(current.bar(), title, progress));
        return true;
    }

    /**
     * Takes the bar down for {@code racer}, if one is shown.
     *
     * @return whether a bar was shown and is now hidden
     */
    public boolean hide(Player racer) {
        Shown removed = bars.remove(racer.getUuid());
        if (removed == null) {
            return false;
        }
        racer.hideBossBar(removed.bar());
        return true;
    }

    /** Drops what is held for a racer who disconnected. */
    public void forget(UUID playerId) {
        bars.remove(playerId);
    }

    /**
     * The bar currently shown to {@code playerId}, if any.
     *
     * <p>Package-private, for {@code LobbyHudTest}. Not part of this class's contract; do not widen it.
     */
    Optional<BossBar> barOf(UUID playerId) {
        return Optional.ofNullable(bars.get(playerId)).map(Shown::bar);
    }
}
