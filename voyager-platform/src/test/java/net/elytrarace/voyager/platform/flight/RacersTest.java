package net.elytrarace.voyager.platform.flight;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The flight equipment of a racer: none while waiting, the elytra and the rockets from the moment a map begins.
 * Each test builds its own instance and player, so the order of the tests does not matter.
 */
@EnvTest
class RacersTest {

    @Test
    void prepareLeavesTheElytraAndTheRocketsOff(Env env) {
        Player racer = waitingRacer(env);

        Racers.prepare(racer);

        assertThat(racer.getEquipment(EquipmentSlot.CHESTPLATE).material()).isNotEqualTo(Material.ELYTRA);
        assertThat(racer.getInventory().getItemStack(0).isAir())
                .describedAs("slot 0 holds no rocket while the racer waits")
                .isTrue();
    }

    @Test
    void equipGivesTheElytraAndRockets(Env env) {
        Player racer = waitingRacer(env);
        Racers.prepare(racer);

        Racers.equip(racer);

        assertThat(racer.getEquipment(EquipmentSlot.CHESTPLATE).material()).isEqualTo(Material.ELYTRA);
        assertThat(racer.getInventory().getItemStack(0).material()).isEqualTo(Material.FIREWORK_ROCKET);
    }

    @Test
    void holdRemovesTheElytraAndRockets(Env env) {
        Player racer = waitingRacer(env);
        Racers.prepare(racer);
        Racers.equip(racer);

        Racers.hold(racer);

        assertThat(racer.getEquipment(EquipmentSlot.CHESTPLATE)).isEqualTo(ItemStack.AIR);
        assertThat(racer.getInventory().getItemStack(0).isAir()).isTrue();
    }

    /**
     * The impulse is {@code LAUNCH_FORWARD_PER_TICK} (0.8) along the horizontal line to the target and
     * {@code LAUNCH_UP_PER_TICK} (1.2) straight up, per tick, and Minestom takes blocks per second, so 20
     * ticks per second scales them to z 16 and y 24.
     */
    @Test
    void aLaunchFromAPointSendsTheHorizontalImpulseAlongTheDirectionAndTheUpwardOne(Env env) {
        Player racer = waitingRacer(env);
        Racers.prepare(racer);

        Racers.launch(racer, new Vec3(0, 60, 0), new Vec3(0, 60, 10));

        Vec velocity = racer.getVelocity();
        assertThat(velocity.x()).isCloseTo(0.0, within(1e-6));
        assertThat(velocity.z()).describedAs("0.8 blocks per tick along +z, in blocks per second")
                .isCloseTo(16.0, within(1e-6));
        assertThat(velocity.y()).describedAs("1.2 blocks per tick straight up, in blocks per second")
                .isCloseTo(24.0, within(1e-6));
    }

    @Test
    void aLaunchFromAPointStartsTheGlide(Env env) {
        Player racer = waitingRacer(env);
        Racers.prepare(racer);

        Racers.launch(racer, new Vec3(0, 60, 0), new Vec3(0, 60, 10));

        assertThat(racer.isFlyingWithElytra()).isTrue();
    }

    /** The map start is now a launch from the spawn toward the first ring, and must give the same impulse. */
    @Test
    void aMapStartLaunchGivesTheSameImpulseAsALaunchFromTheSpawnTowardTheFirstRing(Env env) {
        Vec3 spawn = new Vec3(0, 60, 0);
        Ring first = new Ring(0, new Vec3(3, 64, 12), new Vec3(0, 0, 1), 5.0, 7, RingType.STANDARD);
        MapDefinition map = new MapDefinition("launch-course", "launch_arena", spawn, List.of(first),
                Duration.ofSeconds(4), new BoostConfig(12, 25), new GuideLine(List.of(), 2, 1.0));
        Player byMap = waitingRacer(env);
        Player byPoint = waitingRacer(env);

        Racers.launch(byMap, map);
        Racers.launch(byPoint, spawn, first.center());

        assertThat(byMap.getVelocity()).isEqualTo(byPoint.getVelocity());
    }

    private static Player waitingRacer(Env env) {
        Instance instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        return env.createPlayer(instance, new Pos(0.5, 60, 0.5));
    }
}
