package net.elytrarace.voyager.setup.adapter;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.mapsetup.exception.DraftWriteFailedException;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.elytrarace.voyager.platform.text.SetupMessages;
import net.elytrarace.voyager.setup.mapsetup.DraftEditor;
import net.elytrarace.voyager.setup.mapsetup.RingDefaults;
import net.elytrarace.voyager.setup.mapsetup.RingFromPose;
import net.elytrarace.voyager.setup.mapsetup.RingPicker;
import net.elytrarace.voyager.setup.mapsetup.exception.InvalidPoseException;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerHandAnimationEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;

import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * The wand's gestures. Right-click places a ring at the builder's eye, looking along the flight; left-click removes the
 * nearest ring the look ray crosses within reach. Each change is saved before it is shown.
 *
 * <p>Right-click arrives twice on a block: as an interaction with the block and as a use of the item (research 006,
 * spike 1.2). The block interaction is cancelled, so no block is placed, and a right-click whose pose repeats the last
 * ring's pose is the same click and places nothing. The dedupe is by pose, not by time, so it needs no clock.
 */
public final class WandListener {

    private final BuilderSessions sessions;

    public WandListener(BuilderSessions sessions) {
        this.sessions = sessions;
    }

    /**
     * Registers the three gestures on the node that receives the server's events.
     *
     * @param events the global handler in the setup server; the process's handler in a test
     */
    public void register(EventNode<Event> events) {
        events.addListener(PlayerUseItemEvent.class, this::onUseItem);
        events.addListener(PlayerBlockInteractEvent.class, this::onBlockInteract);
        events.addListener(PlayerHandAnimationEvent.class, this::onSwing);
    }

    private void onUseItem(PlayerUseItemEvent event) {
        if (Wand.is(event.getItemStack())) {
            place(event.getPlayer());
        }
    }

    private void onBlockInteract(PlayerBlockInteractEvent event) {
        if (!Wand.is(event.getPlayer().getItemInHand(event.getHand()))) {
            return;
        }
        event.setCancelled(true);
        place(event.getPlayer());
    }

    private void onSwing(PlayerHandAnimationEvent event) {
        if (!Wand.is(event.getPlayer().getItemInHand(event.getHand()))) {
            return;
        }
        event.setCancelled(true);
        remove(event.getPlayer());
    }

    private void place(Player builder) {
        MapSession session = openSession(builder);
        if (session == null) {
            return;
        }
        Vec3 eye = eyeOf(builder);
        Vec3 look = Vectors.toDomain(builder.getPosition().direction());
        Ring ring;
        try {
            ring = RingFromPose.create(eye, look, session.draft().rings().size());
        } catch (InvalidPoseException exception) {
            builder.sendMessage(SetupMessages.ringPoseRefused());
            return;
        }
        if (repeatsLastPose(session.draft().rings(), ring)) {
            return;
        }
        try {
            session.commit(DraftEditor.withRingAppended(session.draft(), ring));
            builder.sendMessage(SetupMessages.ringPlaced(ring.index()));
        } catch (DraftWriteFailedException exception) {
            builder.sendMessage(SetupMessages.saveFailed(session.id().value()));
        }
    }

    private void remove(Player builder) {
        MapSession session = openSession(builder);
        if (session == null) {
            return;
        }
        OptionalInt hit = RingPicker.nearestCrossed(session.draft().rings(), eyeOf(builder),
                Vectors.toDomain(builder.getPosition().direction()), RingDefaults.REACH_BLOCKS);
        if (hit.isEmpty()) {
            builder.sendMessage(SetupMessages.ringNone());
            return;
        }
        try {
            session.commit(DraftEditor.withoutRing(session.draft(), hit.getAsInt()));
            builder.sendMessage(SetupMessages.ringRemoved(hit.getAsInt()));
        } catch (DraftWriteFailedException exception) {
            builder.sendMessage(SetupMessages.saveFailed(session.id().value()));
        }
    }

    /** The builder's open map, when the builder stands in it; otherwise the builder is told, or left alone. */
    private MapSession openSession(Player builder) {
        UUID uuid = builder.getUuid();
        MapSession session = sessions.find(uuid).orElse(null);
        if (session == null) {
            builder.sendMessage(SetupMessages.noMapOpen());
            return null;
        }
        return builder.getInstance() == session.instance() ? session : null;
    }

    private static boolean repeatsLastPose(List<Ring> rings, Ring ring) {
        if (rings.isEmpty()) {
            return false;
        }
        Ring last = rings.getLast();
        return last.center().equals(ring.center()) && last.normal().equals(ring.normal());
    }

    private static Vec3 eyeOf(Player builder) {
        return Vectors.toDomain(builder.getPosition().add(0, builder.getEyeHeight(), 0));
    }
}
