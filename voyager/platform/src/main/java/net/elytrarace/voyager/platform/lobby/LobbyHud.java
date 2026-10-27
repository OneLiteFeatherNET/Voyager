package net.elytrarace.voyager.platform.lobby;

import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What a waiting racer sees of the lobby on their own screen: the action bar line, and the experience bar, whose level is
 * the whole seconds left of the start and whose fill is the fraction of the lobby that is left.
 *
 * <p>One state per racer, so each line is rendered in that racer's language. The state is shown only while the racer stands
 * in the lobby instance, and it is cleared when the racer leaves it or the countdown is cancelled. A packet is sent only when
 * the line or the bar changes, not every tick. The lobby has no boss bar.
 */
@NotNullByDefault
public final class LobbyHud {

    /** What a racer's HUD currently shows, so an unchanged state is recognised without a packet. */
    private record Shown(Component actionBar, int level, float fraction) {
    }

    private final Map<UUID, Shown> shown = new HashMap<>();

    /**
     * Shows the lobby HUD to {@code racer} if the racer stands in {@code lobby}, and clears it if not.
     *
     * @param racer    the racer the HUD is for
     * @param lobby    the lobby instance; the HUD shows only while the racer is in it
     * @param actionBar the line for the action bar
     * @param level    the experience level: the whole seconds left of the countdown, or zero
     * @param fraction the experience bar's fill, from zero to one: the fraction of the lobby that is left, or zero
     * @return whether what the racer sees changed, so a caller can count the updates it sent
     */
    public boolean update(Player racer, Instance lobby, Component actionBar, int level, float fraction) {
        if (racer.getInstance() != lobby) {
            return hide(racer);
        }
        UUID id = racer.getUuid();
        Shown current = shown.get(id);
        if (current != null && current.actionBar().equals(actionBar) && current.level() == level
                && Float.compare(current.fraction(), fraction) == 0) {
            return false;
        }
        // Each part is sent only when it changes: the action bar line changes once a second at most, while the bar drains
        // every tick.
        if (current == null || !current.actionBar().equals(actionBar)) {
            racer.sendActionBar(actionBar);
        }
        if (current == null || current.level() != level) {
            racer.setLevel(level);
        }
        if (current == null || Float.compare(current.fraction(), fraction) != 0) {
            racer.setExp(fraction);
        }
        shown.put(id, new Shown(actionBar, level, fraction));
        return true;
    }

    /**
     * Clears the HUD for {@code racer}: the action bar goes empty, and the experience level and bar go to zero.
     *
     * @return whether a HUD was shown and is now cleared
     */
    public boolean hide(Player racer) {
        Shown removed = shown.remove(racer.getUuid());
        if (removed == null) {
            return false;
        }
        racer.sendActionBar(Component.empty());
        racer.setLevel(0);
        racer.setExp(0.0f);
        return true;
    }

    /** Drops what is held for a racer who disconnected. */
    public void forget(UUID playerId) {
        shown.remove(playerId);
    }

    /**
     * The action bar line currently shown to {@code playerId}, if any.
     *
     * <p>Package-private, for {@code LobbyHudTest}. Not part of this class's contract; do not widen it.
     */
    Optional<Component> actionBarOf(UUID playerId) {
        return Optional.ofNullable(shown.get(playerId)).map(Shown::actionBar);
    }
}
