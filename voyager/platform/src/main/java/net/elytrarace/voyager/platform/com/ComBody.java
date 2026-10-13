package net.elytrarace.voyager.platform.com;

import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.avatar.PlayerMeta;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The Minestom body of one computer racer: a plain {@link Entity} of the player type, with every skin layer switched on
 * and its seat name drawn above its head.
 *
 * <p>The body is not a {@code Player}. It has no connection, is never in the online players, and never takes part in a
 * join, a handoff or a permission check. Its tab-list entry is the business of {@code ComTabList}, not this class.
 *
 * <p>The body takes no gravity. Nothing on the server moves it between the field's own moves, so a body that is left
 * alone, a finished racer for example, stays at the height it was last put at instead of falling through the course.
 *
 * <p>This is the one seam for the body's shape. If a playtest finds the client hiding a player-type entity without a
 * connection, the armour-stand fallback replaces the entity inside this class and nothing else changes.
 */
public final class ComBody {

    private final Entity entity;

    /**
     * Builds a body that is not yet in any instance.
     *
     * @param id the racer's id, the same id the cup runs the racer's run under
     * @param name the seat name drawn above the body, translated, for example {@code COM 1}
     */
    public ComBody(UUID id, Component name) {
        this.entity = new Entity(EntityType.PLAYER, id);
        entity.setNoGravity(true);
        PlayerMeta meta = (PlayerMeta) entity.getEntityMeta();
        meta.setCapeEnabled(true);
        meta.setJacketEnabled(true);
        meta.setLeftSleeveEnabled(true);
        meta.setRightSleeveEnabled(true);
        meta.setLeftLegEnabled(true);
        meta.setRightLegEnabled(true);
        meta.setHatEnabled(true);
        entity.setCustomName(name);
        entity.setCustomNameVisible(true);
    }

    /**
     * Puts the body into an instance.
     *
     * @param instance the instance to spawn into
     * @param position where the body stands
     * @return a future that completes when the body is in the instance
     */
    public CompletableFuture<Void> spawn(Instance instance, Pos position) {
        return entity.setInstance(instance, position);
    }

    /**
     * Moves the body. The position changes on the next tick.
     *
     * @param position the new position
     * @return a future that completes when the move has been applied
     */
    public CompletableFuture<Void> teleport(Pos position) {
        return entity.teleport(position);
    }

    /** Removes the body from its instance. It is gone after the next tick. */
    public void remove() {
        entity.remove();
    }

    /**
     * @return the instance the body is in, or {@code null} before it is spawned and after it is removed
     */
    public @Nullable Instance instance() {
        return entity.getInstance();
    }

    /** @return where the body stands */
    public Pos position() {
        return entity.getPosition();
    }

    /** @return {@code true} once the body has been removed */
    public boolean isRemoved() {
        return entity.isRemoved();
    }

    /** @return the name drawn above the body, or {@code null} when it has none */
    public @Nullable Component customName() {
        return entity.getCustomName();
    }

    /** @return {@code true} when the name above the body is drawn */
    public boolean isCustomNameVisible() {
        return entity.isCustomNameVisible();
    }
}
