package net.elytrarace.voyager.platform.handoff;

import net.elytrarace.voyager.api.handoff.PlayerHandoff;
import net.elytrarace.voyager.api.handoff.PlayerHandoffs;
import net.elytrarace.voyager.api.handoff.exception.HandoffUnavailableException;
import net.elytrarace.voyager.platform.text.Messages;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.Collection;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Moves every racer of a finished cup to the network hub.
 *
 * <p>Each racer is asked through the installed {@link PlayerHandoff}. With none installed, which is a local run without the
 * CloudNet bridge, the racer is disconnected with {@link Messages#handoffDisconnect()}. A refused handoff takes the same
 * path, and logs a warning that names the racer. Nothing is retried (ADR-0033, Q2). A racer is never kicked on the normal
 * path: the disconnect is the fallback only.
 *
 * <p>The handoff and the disconnect are injected. {@link #fromSlot} reads the process-wide slot the bridge fills; the
 * composition root passes the Minestom disconnect and its logger.
 */
@NotNullByDefault
public final class HubHandoff {

    /** Where the warning for a refused handoff goes. The composition root passes its logger. */
    @FunctionalInterface
    public interface Warnings {

        /**
         * @param message the line to warn with, naming the racer
         */
        void warn(String message);
    }

    private final Supplier<Optional<PlayerHandoff>> installed;
    private final BiConsumer<Player, Component> disconnect;
    private final Warnings warnings;

    /**
     * @param installed  the handoff to ask, read on every send; empty when none is installed
     * @param disconnect removes a racer from the server with the given reason
     * @param warnings   receives the warning for a refused handoff
     */
    public HubHandoff(Supplier<Optional<PlayerHandoff>> installed, BiConsumer<Player, Component> disconnect,
            Warnings warnings) {
        this.installed = installed;
        this.disconnect = disconnect;
        this.warnings = warnings;
    }

    /**
     * The hub handoff that reads the slot the CloudNet bridge fills when it starts ({@link PlayerHandoffs#SLOT}).
     *
     * @param disconnect removes a racer from the server with the given reason
     * @param warnings   receives the warning for a refused handoff
     * @return the hub handoff
     */
    public static HubHandoff fromSlot(BiConsumer<Player, Component> disconnect, Warnings warnings) {
        return new HubHandoff(PlayerHandoffs.SLOT::current, disconnect, warnings);
    }

    /**
     * Sends each racer to the hub, in the order given.
     *
     * <p>TODO(com-bots): a COM racer is a bot, and a bot must never be handed off. The caller decides who is passed here, in
     * {@code WaitingRoom.handoffCandidates()}; that is the one place to exclude bots once the bot change lands. No bot exists
     * yet, so every racer passed here is a human.
     *
     * @param racers the racers to move
     */
    public void send(Collection<Player> racers) {
        Optional<PlayerHandoff> handoff = installed.get();
        for (Player racer : racers) {
            if (handoff.isEmpty()) {
                disconnect.accept(racer, Messages.handoffDisconnect());
                continue;
            }
            try {
                handoff.get().toHub(racer.getUuid());
            } catch (HandoffUnavailableException refused) {
                warnings.warn("Racer %s could not be handed to the hub and is disconnected: %s"
                        .formatted(racer.getUsername(), refused.getMessage()));
                disconnect.accept(racer, Messages.handoffDisconnect());
            }
        }
    }
}
