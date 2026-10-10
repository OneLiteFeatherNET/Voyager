package net.elytrarace.voyager.platform.flight;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static Player waitingRacer(Env env) {
        Instance instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        return env.createPlayer(instance, new Pos(0.5, 60, 0.5));
    }
}
