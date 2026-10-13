package net.elytrarace.voyager.platform.flight;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.platform.hud.RaceFeedback;
import net.kyori.adventure.text.TranslatableComponent;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.SetTitleSubTitlePacket;
import net.minestom.server.network.packet.server.play.SetTitleTextPacket;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;

import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * The flight equipment of a racer: none while waiting, the elytra and the rockets from the moment a map begins.
 * Each test builds its own instance and player, so the order of the tests does not matter.
 */
@EnvTest
class RacersTest {

    /** The tuning of a map that states nothing new: 64 rockets, a 30-tick burn. */
    private static final BoostConfig DEFAULT = new BoostConfig(30, 40);

    /** A count that is not the default, so a racer given 64 by accident fails the stack check. */
    private static final BoostConfig TWELVE_ROCKETS = new BoostConfig(30, 40, 12, null);

    @Test
    void aMapsCountIsTheStackARacerIsGiven(Env env) {
        Player racer = waitingRacer(env);
        Racers.prepare(racer);

        Racers.equip(racer, TWELVE_ROCKETS);

        assertThat(racer.getInventory().getItemStack(0).amount()).isEqualTo(12);
    }

    /** A reset is a fresh loadout, so the racer gets the map's count back after a stack was spent. */
    @Test
    void aResetGivesTheMapsCountAgain(Env env) {
        Player racer = waitingRacer(env);
        Racers.prepare(racer);
        racer.getInventory().setItemStack(0, ItemStack.of(Material.FIREWORK_ROCKET, 2));

        Racers.place(racer, new Vec3(0, 60, 10), TWELVE_ROCKETS);

        assertThat(racer.getInventory().getItemStack(0).amount()).isEqualTo(12);
    }

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

        Racers.equip(racer, DEFAULT);

        assertThat(racer.getEquipment(EquipmentSlot.CHESTPLATE).material()).isEqualTo(Material.ELYTRA);
        assertThat(racer.getInventory().getItemStack(0).material()).isEqualTo(Material.FIREWORK_ROCKET);
    }

    @Test
    void holdRemovesTheElytraAndRockets(Env env) {
        Player racer = waitingRacer(env);
        Racers.prepare(racer);
        Racers.equip(racer, DEFAULT);

        Racers.hold(racer);

        assertThat(racer.getEquipment(EquipmentSlot.CHESTPLATE)).isEqualTo(ItemStack.AIR);
        assertThat(racer.getInventory().getItemStack(0).isAir()).isTrue();
    }

    /** A reset places the racer facing the target and ends any glide they were in; it starts none. */
    @Test
    void placeEquipsTheRacerAndEndsTheGlideWithoutStartingOne(Env env) {
        Player racer = waitingRacer(env);
        Racers.prepare(racer);
        racer.setFlyingWithElytra(true);

        Racers.place(racer, new Vec3(0, 60, 10), DEFAULT);

        assertThat(racer.getEquipment(EquipmentSlot.CHESTPLATE).material()).isEqualTo(Material.ELYTRA);
        assertThat(racer.isFlyingWithElytra())
                .describedAs("a reset out of bounds in mid-flight ends the glide; the racer deploys again to fly")
                .isFalse();
    }

    /**
     * The GO title and the take-off subtitle arrive in the same tick, so the racer reads "jump, then deploy" at the
     * moment the count ends. Asserted on the translation key, which is the text a client renders.
     */
    @Test
    void goShowsTheJumpAndDeployTitle(Env env) {
        Instance instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        TestConnection connection = env.createConnection();
        Player racer = connection.connect(instance, new Pos(0.5, 60, 0.5));
        Collector<SetTitleTextPacket> titles = connection.trackIncoming(SetTitleTextPacket.class);
        Collector<SetTitleSubTitlePacket> subtitles = connection.trackIncoming(SetTitleSubTitlePacket.class);

        RaceFeedback.go(racer);
        env.tick();

        assertThat(titles.collect())
                .describedAs("the GO title is sent in the tick of the launch")
                .hasSize(1);
        assertThat(subtitles.collect().stream().map(SetTitleSubTitlePacket::subtitle))
                .describedAs("one subtitle, in the same tick, telling the racer to jump and deploy the elytra")
                .singleElement()
                .isInstanceOfSatisfying(TranslatableComponent.class,
                        text -> assertThat(text.key()).isEqualTo("voyager.race.go.jump"));
    }

    private static Player waitingRacer(Env env) {
        Instance instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        return env.createPlayer(instance, new Pos(0.5, 60, 0.5));
    }
}
