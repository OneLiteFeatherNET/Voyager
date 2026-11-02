package net.elytrarace.voyager.platform.flight;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.platform.hud.RaceFeedback;
import net.kyori.adventure.text.TranslatableComponent;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
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

        Racers.place(racer, new Vec3(0, 60, 10), TWELVE_ROCKETS, true);

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

    /** A checkpoint placement leaves the racer gliding, and sends no velocity of its own: the velocity is untouched. */
    @Test
    void placeSetsTheGlideWithoutAVelocity(Env env) {
        Player racer = waitingRacer(env);
        Racers.prepare(racer);
        racer.setVelocity(new Vec(0, -20, 0));

        Racers.place(racer, new Vec3(0, 60, 10), DEFAULT, true);

        assertThat(racer.isFlyingWithElytra())
                .describedAs("a checkpoint reset places the racer gliding, with no impulse")
                .isTrue();
        assertThat(racer.getVelocity())
                .describedAs("place sends no velocity: the one velocity a reset sends is zeroed by RunResetter")
                .isEqualTo(new Vec(0, -20, 0));
    }

    /** A spawn placement is grounded, as at GO: the racer is equipped, facing the target, and not gliding. */
    @Test
    void placeWithoutAGlideEndsAnyGlide(Env env) {
        Player racer = waitingRacer(env);
        Racers.prepare(racer);
        racer.setFlyingWithElytra(true);

        Racers.place(racer, new Vec3(0, 60, 10), DEFAULT, false);

        assertThat(racer.getEquipment(EquipmentSlot.CHESTPLATE).material()).isEqualTo(Material.ELYTRA);
        assertThat(racer.isFlyingWithElytra())
                .describedAs("a spawn reset ends the glide; the racer deploys the elytra again to take off")
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

    /** A held racer's flight carries no speed, so the client cannot fly them anywhere, and the flight is on. */
    @Test
    void freezeGivesAFlightThatCarriesNoSpeed(Env env) {
        Player racer = waitingRacer(env);

        Racers.freeze(racer);

        assertThat(racer.isFlying()).describedAs("flying while held").isTrue();
        assertThat(racer.isAllowFlying()).describedAs("allowed to fly while held").isTrue();
        assertThat(racer.getFlyingSpeed()).describedAs("flying speed while held").isZero();
    }

    /** A racer who had no flight gets none back when the hold ends: off, not allowed, at the default speed. */
    @Test
    void thawPutsBackTheFlightARacerHadBeforeTheHold(Env env) {
        Player racer = waitingRacer(env);
        Racers.prepare(racer);

        Racers.freeze(racer).thaw();

        assertThat(racer.isFlying()).isFalse();
        assertThat(racer.isAllowFlying()).isFalse();
        assertThat(racer.getFlyingSpeed()).isEqualTo(0.05f);
    }

    /** A racer who already had a flight keeps it, speed and all, when the hold ends. */
    @Test
    void thawKeepsTheFlightARacerAlreadyHad(Env env) {
        Player racer = waitingRacer(env);
        racer.setAllowFlying(true);
        racer.setFlying(true);
        racer.setFlyingSpeed(0.2f);

        Racers.freeze(racer).thaw();

        assertThat(racer.isFlying()).isTrue();
        assertThat(racer.isAllowFlying()).isTrue();
        assertThat(racer.getFlyingSpeed()).isEqualTo(0.2f);
    }

    /** A held racer who double-tapped their flight off is hovering again once the hold is kept. */
    @Test
    void keepFrozenPutsTheFlightBackWhenTheClientTurnedItOff(Env env) {
        Player racer = waitingRacer(env);
        Racers.freeze(racer);
        racer.setFlying(false);

        Racers.keepFrozen(racer);

        assertThat(racer.isFlying()).isTrue();
    }

    /** A held racer who has drifted is returned to the hold, and keeps the way they are looking. */
    @Test
    void pinReturnsADriftingRacerToTheHoldAndKeepsTheirHead(Env env) {
        Player racer = waitingRacer(env);
        Racers.freeze(racer);
        racer.teleport(new Pos(1.5, 60, 0.5, 45f, 0f)).join();

        Racers.pin(racer, new Pos(0.5, 60, 0.5, 0f, 0f));

        assertThat(racer.getPosition().distance(new Pos(0.5, 60, 0.5)))
                .describedAs("distance from the hold after the pin")
                .isLessThanOrEqualTo(0.1);
        assertThat(racer.getPosition().yaw()).describedAs("the head is not turned back by the pin").isEqualTo(45f);
    }

    private static Player waitingRacer(Env env) {
        Instance instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        return env.createPlayer(instance, new Pos(0.5, 60, 0.5));
    }
}
