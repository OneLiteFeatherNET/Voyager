package net.elytrarace.voyager.platform.com;

import net.kyori.adventure.text.Component;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket;
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket;
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket.Action;
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket.Entry;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The tab-list entries of the computer racers, shown to the human players and to nobody else.
 *
 * <p>A computer racer is not a player, so the client knows nothing about it until an entry is sent. The entry carries
 * the seat name and no texture property, so the client draws the default skin (D2, OQ6). Every human who is online gets
 * the entries of all current racers before their bodies are spawned, and a human who joins later is brought up to date
 * by {@link #sync()}, which the field calls once per tick.
 *
 * <p>The humans come from a supplier that is read on every call, so the roster is never a snapshot. This class holds
 * which humans have been told about the current entries and forgets a human who has left.
 */
public final class ComTabList {

    private static final EnumSet<Action> ADD_ACTIONS =
            EnumSet.of(Action.ADD_PLAYER, Action.UPDATE_LISTED, Action.UPDATE_DISPLAY_NAME);

    private final Supplier<Collection<Player>> humans;

    /** The entries on show, in the order they were added. */
    private final Map<UUID, String> entries = new LinkedHashMap<>();

    /** The humans who have been sent every entry on show. */
    private final Set<UUID> informed = new HashSet<>();

    /**
     * @param humans the human players, read on every call; never contains a computer racer
     */
    public ComTabList(Supplier<Collection<Player>> humans) {
        this.humans = humans;
    }

    /**
     * Adds a computer racer's entry and sends it to every human. A human who has not yet been told about the current
     * entries is sent all of them instead.
     *
     * @param id the computer racer's id, the same id its body carries
     * @param name the seat name, for example {@code COM 1}
     */
    public void add(UUID id, String name) {
        entries.put(id, name);
        for (Player human : humans.get()) {
            if (informed.contains(human.getUuid())) {
                human.sendPacket(new PlayerInfoUpdatePacket(ADD_ACTIONS, entryOf(id, name)));
            } else {
                inform(human);
            }
        }
    }

    /**
     * Sends every entry on show to each human who has not been sent them yet. A human who has left is forgotten, so a
     * later join by the same player is told again.
     */
    public void sync() {
        Set<UUID> online = new HashSet<>();
        for (Player human : humans.get()) {
            online.add(human.getUuid());
        }
        informed.retainAll(online);
        for (Player human : humans.get()) {
            if (!informed.contains(human.getUuid())) {
                inform(human);
            }
        }
    }

    /**
     * Removes a computer racer's entry from every human's tab list.
     *
     * @param id the computer racer's id
     */
    public void remove(UUID id) {
        if (entries.remove(id) == null) {
            return;
        }
        PlayerInfoRemovePacket packet = new PlayerInfoRemovePacket(id);
        for (Player human : humans.get()) {
            human.sendPacket(packet);
        }
    }

    /**
     * Removes every computer racer's entry from every human's tab list, and forgets them all. Called when the cup ends
     * or is aborted.
     */
    public void dismiss() {
        if (entries.isEmpty()) {
            return;
        }
        PlayerInfoRemovePacket packet = new PlayerInfoRemovePacket(List.copyOf(entries.keySet()));
        for (Player human : humans.get()) {
            human.sendPacket(packet);
        }
        entries.clear();
        informed.clear();
    }

    /** Sends every entry on show to one human, and records that they have them. */
    private void inform(Player human) {
        if (!entries.isEmpty()) {
            List<Entry> all = entries.entrySet().stream()
                    .map(entry -> entryOf(entry.getKey(), entry.getValue()))
                    .toList();
            human.sendPacket(new PlayerInfoUpdatePacket(ADD_ACTIONS, all));
        }
        informed.add(human.getUuid());
    }

    /** The entry for one computer racer: listed, named after its seat, with no texture and no game mode of its own. */
    private static Entry entryOf(UUID id, String name) {
        return new Entry(id, name, List.of(), true, 0, GameMode.SURVIVAL, Component.text(name), null, 0, true);
    }
}
