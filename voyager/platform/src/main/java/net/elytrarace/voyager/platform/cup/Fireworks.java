package net.elytrarace.voyager.platform.cup;

import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.util.RGBLike;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.projectile.FireworkRocketMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.FireworkExplosion;
import net.minestom.server.item.component.FireworkList;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A firework burst for the cup's finale: a firework rocket entity that carries its explosion, set off where it is spawned.
 *
 * <p>The pinned Minestom sets the explosion on the rocket's metadata, and the client draws it when the rocket's explode
 * status arrives, so the burst is the rocket and no particle fallback is needed. The rocket is removed in the same tick.
 */
@ApiStatus.Internal
public final class Fireworks {

    /** The entity status that makes a firework rocket explode now, the vanilla value. */
    private static final byte EXPLODE_NOW = 17;

    /** Ticks the rocket flies before it explodes: none, so the explosion is the whole effect. */
    private static final int FLIGHT_DURATION = 0;

    private Fireworks() {
    }

    /**
     * Sets a firework off at {@code position}, in {@code instance}, in one colour.
     *
     * @param instance the instance the racer is in
     * @param position where the burst goes off
     * @param colour   the colour of the burst
     */
    public static CompletableFuture<Void> burst(Instance instance, Pos position, TextColor colour) {
        Entity rocket = rocketFor(colour);
        CompletableFuture<Void> spawned = rocket.setInstance(instance, position);
        rocket.triggerStatus(EXPLODE_NOW);
        rocket.remove();
        return spawned;
    }

    /** An unspawned rocket whose explosion is a large ball of {@code colour}. */
    static Entity rocketFor(TextColor colour) {
        FireworkExplosion explosion = new FireworkExplosion(FireworkExplosion.Shape.LARGE_BALL,
                List.<RGBLike>of(colour), List.of(), false, true);
        ItemStack info = ItemStack.of(Material.FIREWORK_ROCKET)
                .with(DataComponents.FIREWORKS, new FireworkList(FLIGHT_DURATION, List.of(explosion)));
        Entity rocket = new Entity(EntityType.FIREWORK_ROCKET);
        ((FireworkRocketMeta) rocket.getEntityMeta()).setFireworkInfo(info);
        return rocket;
    }
}
