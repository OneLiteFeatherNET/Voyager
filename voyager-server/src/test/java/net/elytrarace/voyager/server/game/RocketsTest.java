package net.elytrarace.voyager.server.game;

import net.elytrarace.voyager.api.race.BoostConfig;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.projectile.FireworkRocketMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.FireworkList;
import net.minestom.server.network.packet.server.play.SetCooldownPacket;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The entity half of the boost, on a live server.
 *
 * <p>What this can and cannot show is worth stating once. It can show that the rocket exists, that it
 * names the right racer as its shooter, and that it is gone after exactly the configured number of
 * ticks — which is the whole of what the server is responsible for. It <strong>cannot</strong> show
 * that a real 26.2 client then accelerates, because there is no client here; that step is derived
 * from Vanilla and stays unverified until somebody flies it. See {@link Rockets}'s class javadoc.
 *
 * <p>Two burns with different lengths, never one: a removal scheduled on a constant rather than on
 * the configuration would satisfy any single-length test in this file.
 */
@EnvTest
class RocketsTest {

    private static final BoostConfig SHORT = new BoostConfig(3, 8);
    private static final BoostConfig LONG = new BoostConfig(6, 13);

    @Test
    void theItemARacerCarriesDeclaresVanillasLongestFlightDuration(Env env) {
        ItemStack rocket = Rockets.item(64);

        assertThat(rocket.material()).isEqualTo(Material.FIREWORK_ROCKET);
        assertThat(rocket.amount()).isEqualTo(64);
        FireworkList fireworks = rocket.get(DataComponents.FIREWORKS);
        assertThat(fireworks).isNotNull();
        assertThat(fireworks.flightDuration())
                .describedAs("three, the longest a player can craft")
                .isEqualTo(3);
        // Not asserted against Rockets.FLIGHT_DURATION, which would only be the constant compared
        // with itself. The claim worth pinning is that the item's tooltip and the server's burn come
        // from the same number: Vanilla's lifetime is 10 * flightDuration, and the seed the map data
        // is built on has to be that product for this rocket.
        assertThat(BoostConfig.VANILLA_BURN_TICKS)
                .describedAs("the burn the map data is seeded with is 10 * the item's own flight duration")
                .isEqualTo(10 * fireworks.flightDuration());
        assertThat(fireworks.explosions())
                .describedAs("a burst of colour every burn is noise in front of the rings")
                .isEmpty();
    }

    /**
     * The metadata that makes a client boost itself: the rocket carries the racer's entity id in
     * {@code SHOOTER_ENTITY_ID}, which is what a vanilla client reads to decide the rocket is
     * attached to its own player.
     */
    @Test
    void firingSpawnsARocketAttachedToTheRacerWhoUsedIt(Env env) {
        Player racer = player(env);

        Rockets.fire(racer, SHORT);

        Entity rocket = onlyRocketIn(racer.getInstance());
        assertThat(rocket.getEntityType()).isEqualTo(EntityType.FIREWORK_ROCKET);
        FireworkRocketMeta meta = (FireworkRocketMeta) rocket.getEntityMeta();
        assertThat(meta.getShooterEntityId())
                .describedAs("the client boosts the entity this id names, and nobody else")
                .isEqualTo(racer.getEntityId());
        assertThat(meta.getFireworkInfo().get(DataComponents.FIREWORKS)).isNotNull();
        assertThat(rocket.getPosition().samePoint(racer.getPosition()))
                .describedAs("spawned on the racer, so it is inside every viewer's tracking range")
                .isTrue();
    }

    /**
     * A racing server cannot let the rocket fall away from the racer it is attached to: it would
     * leave its own tracking range part-way through a burn the racer is still feeling, and vanish
     * from every other player's screen.
     */
    @Test
    void theRocketNeitherFallsNorCollidesWithAnything(Env env) {
        Player racer = player(env);

        Rockets.fire(racer, LONG);
        Entity rocket = onlyRocketIn(racer.getInstance());
        Pos spawnedAt = rocket.getPosition();
        for (int tick = 0; tick < 3; tick++) {
            env.tick();
        }

        assertThat(rocket.hasNoGravity()).isTrue();
        assertThat(rocket.hasPhysics()).isFalse();
        assertThat(rocket.getPosition().samePoint(spawnedAt))
                .describedAs("three ticks of gravity would have moved it")
                .isTrue();
    }

    /**
     * The determinism the whole design turns on. Vanilla's rocket picks its own lifetime with two
     * dice rolls; here the server removes it after exactly {@code burnDurationTicks}, so two
     * identical boosts are worth the same.
     */
    @Test
    void theRocketIsRemovedAfterExactlyTheConfiguredNumberOfTicks(Env env) {
        Player racer = player(env);

        Rockets.fire(racer, SHORT);
        Entity rocket = onlyRocketIn(racer.getInstance());

        for (int tick = 0; tick < SHORT.burnDurationTicks() - 1; tick++) {
            env.tick();
        }
        assertThat(rocket.isRemoved())
                .describedAs("still burning one tick before the end of a %s-tick burn",
                        SHORT.burnDurationTicks())
                .isFalse();

        env.tick();

        assertThat(rocket.isRemoved())
                .describedAs("gone on the %sth tick, not the one after it — measured, not assumed",
                        SHORT.burnDurationTicks())
                .isTrue();
    }

    /**
     * The length comes from the configuration, not from a constant. Fired with the longer tuning, the
     * rocket is still there at the tick the shorter one would already have been removed on.
     */
    @Test
    void aLongerBurnKeepsItsRocketPastTheEndOfAShorterOne(Env env) {
        Player racer = player(env);

        Rockets.fire(racer, LONG);
        Entity rocket = onlyRocketIn(racer.getInstance());
        for (int tick = 0; tick <= SHORT.burnDurationTicks(); tick++) {
            env.tick();
        }

        assertThat(rocket.isRemoved())
                .describedAs("a %s-tick burn outlives the %s-tick one's removal",
                        LONG.burnDurationTicks(), SHORT.burnDurationTicks())
                .isFalse();
    }

    /**
     * The client is told how long the wait is, in the group vanilla keys a firework's cooldown under.
     *
     * <p>Cosmetic in the sense that the server enforces the cooldown either way — and load-bearing in
     * the sense that a racer who sees no greyed-out stack has no way to know when the next rocket is
     * available, and finds out by pressing the button and having nothing happen. The tick count is
     * the cooldown, not the burn: {@link #LONG} carries different numbers for the two so that reading
     * the wrong one shows here.
     */
    @Test
    void firingTellsTheClientHowLongUntilTheNextRocket(Env env) {
        Instance instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        TestConnection connection = env.createConnection();
        Player racer = connection.connect(instance, new Pos(0.5, 60, 0.5));
        Collector<SetCooldownPacket> cooldowns = connection.trackIncoming(SetCooldownPacket.class);

        Rockets.fire(racer, LONG);

        cooldowns.assertSingle(packet -> {
            assertThat(packet.cooldownTicks()).isEqualTo(LONG.cooldownTicks());
            assertThat(packet.cooldownGroup())
                    .describedAs("vanilla keys an item with no use_cooldown component by its own id")
                    .isEqualTo("minecraft:firework_rocket");
        });
    }

    private static Player player(Env env) {
        Instance instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        return env.createPlayer(instance, new Pos(0.5, 60, 0.5));
    }

    private static Entity onlyRocketIn(Instance instance) {
        List<Entity> rockets = instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.FIREWORK_ROCKET)
                .toList();
        assertThat(rockets).describedAs("firework rockets in the instance").hasSize(1);
        return rockets.getFirst();
    }
}
