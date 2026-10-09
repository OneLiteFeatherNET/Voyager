package net.elytrarace.voyager.server.game;

import net.elytrarace.voyager.api.race.BoostConfig;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.projectile.FireworkRocketMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.FireworkList;
import net.minestom.server.network.packet.server.play.SetCooldownPacket;
import net.minestom.server.utils.time.TimeUnit;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

import java.util.List;

/**
 * The rocket itself: the item a racer carries, and the entity the server spawns when one is used.
 *
 * <h2>Why a real entity, and not a velocity</h2>
 *
 * <p>The tree being replaced computed the boost server-side and pushed the result with
 * {@code setVelocity}. That is the pattern this rebuild exists to stop: normal elytra flight is
 * client-authoritative, and a server that shoves is a server fighting the client rather than agreeing
 * with it — every packet of correction shows up as the rubber-banding the old tree had.
 *
 * <p>So this does what Vanilla does. It spawns a real firework rocket entity with the racer as its
 * shooter ({@code FireworkRocketMeta.setShooter}, which writes
 * {@code MetadataDef.FireworkRocketEntity.SHOOTER_ENTITY_ID}), and the client — which ticks that
 * entity locally and, seeing itself as the attachment, applies the impulse to its own player — boosts
 * itself. The server applies no velocity at all; it only mirrors the same impulse into its own shadow
 * simulation, through {@code LivePlayerSampler} reporting the burn.
 *
 * <p><strong>This is derived from Vanilla, not observed.</strong> Minestom carries the metadata and
 * no boost logic of its own — exactly the situation the elytra flag was in until somebody flew it —
 * so whether a 26.2 client actually accelerates here is unverified until a real client has been in
 * the air with it. If it turns out not to, the sanctioned fallback is one more external force through
 * {@code VelocityExit}, and the change is confined to this class.
 *
 * <h2>Why the server removes the rocket</h2>
 *
 * <p>Vanilla's rocket picks its own lifetime with two dice rolls. A race in which two identical
 * boosts are worth different amounts of speed is not a race, so the entity is removed after exactly
 * {@link BoostConfig#burnDurationTicks()} ticks and nothing random is left in it. The tick count is
 * scheduled on the entity's own scheduler in {@link TimeUnit#SERVER_TICK}, so it is a count of ticks
 * rather than a wall-clock delay that a slow tick would stretch.
 */
@ApiStatus.Internal
public abstract class Rockets {

    /**
     * The flight duration written into the item the racer holds.
     *
     * <p>Three, the maximum a player can craft, and the same three
     * {@link BoostConfig#VANILLA_BURN_TICKS} is derived from — so what the item claims and how long
     * the server burns it agree. A rocket whose tooltip says one thing while the server does another
     * is a discrepancy a player notices and cannot explain.
     */
    static final int FLIGHT_DURATION = 3;

    /**
     * The cooldown group the client greys the stack out under.
     *
     * <p>Vanilla's {@code ItemCooldowns} keys a cooldown by the item's {@code use_cooldown}
     * component's group when it has one and by the item's own id when it does not. The rocket handed
     * out here has no such component — the cooldown is per map and the item is handed out before a
     * map is known — so this is the id, verbatim.
     */
    private static final String COOLDOWN_GROUP = "minecraft:firework_rocket";

    private Rockets() {
    }

    /**
     * The item a racer carries, {@code count} of them in one stack.
     *
     * <p>The stack is never consumed: the cooldown is what limits a racer, not the number of rockets
     * they happen to be holding. A race decided by somebody running out three rings from the end is a
     * race decided by inventory management, and the committed course climbs 380 blocks — running out
     * on it is not a challenge, it is a stranding.
     */
    @Contract(pure = true)
    public static ItemStack item(int count) {
        return ItemStack.builder(Material.FIREWORK_ROCKET)
                .amount(count)
                // No explosions: this rocket exists to boost, and a burst of colour at the end of
                // every burn on a course flown at 60 blocks a second is noise in front of the rings.
                .set(DataComponents.FIREWORKS, new FireworkList(FLIGHT_DURATION, List.of()))
                .build();
    }

    /**
     * Spawns one rocket attached to {@code shooter} and tells their client how long the next one is.
     *
     * <p>Called only after {@code FireworkBoostTracker} has agreed to the boost, so this does not
     * check the cooldown or the gliding flag — the tracker is the one place those are decided, and a
     * second copy of the rule here would be a second answer to maintain.
     *
     * @param shooter the racer boosting; must be in an instance
     * @param config the tuning of the map being raced
     */
    public static void fire(Player shooter, BoostConfig config) {
        Instance instance = shooter.getInstance();
        if (instance == null) {
            return;
        }
        Entity rocket = new Entity(EntityType.FIREWORK_ROCKET);
        FireworkRocketMeta meta = (FireworkRocketMeta) rocket.getEntityMeta();
        meta.setFireworkInfo(item(1));
        meta.setShooter(shooter);
        // The client puts the rocket at whatever it is attached to on every one of its own ticks, so
        // the server neither moves it nor lets it fall. Gravity here would only make the entity drift
        // out of its own tracking range and vanish from other players' screens part-way through a
        // burn the racer is still feeling.
        rocket.setNoGravity(true);
        rocket.setHasPhysics(false);
        rocket.setInstance(instance, shooter.getPosition());
        rocket.scheduleRemove(config.burnDurationTicks(), TimeUnit.SERVER_TICK);

        shooter.sendPacket(new SetCooldownPacket(COOLDOWN_GROUP, config.cooldownTicks()));
    }
}
