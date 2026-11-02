package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.race.flow.StartGate;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.play.ClientEntityActionPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerAbilitiesPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerRotationPacket;
import net.minestom.server.network.packet.client.play.ClientTeleportConfirmPacket;
import net.minestom.server.network.packet.server.play.PlayerAbilitiesPacket;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * A racer held on a map before GO: hovering in place, so their own client can neither move them nor start a glide,
 * while their head stays free. The hover is taken away again at GO, at an abort or disarm, and when they disconnect.
 * Played through the cup on a live test server, ticked by hand, with the client's packets queued the way the server
 * reads them.
 *
 * <p>A client's packet is read on the tick after it is queued, after that tick's cup step. So the effect of a packet
 * is asserted on the tick it is read, and the effect of a cup step on the tick the step runs.
 */
@EnvTest
class HeldRacerTest {

    /** Lobby of two seconds, a one-second race, and a short end between maps. */
    private static final RaceTimings TIMINGS = new RaceTimings(
            Duration.ofSeconds(2), Duration.ofSeconds(1), Duration.ofMillis(200), Duration.ofMillis(200));

    /** A lobby long enough to be disarmed: the countdown is open until its last three seconds. */
    private static final RaceTimings OPEN_LOBBY = new RaceTimings(
            Duration.ofSeconds(10), Duration.ofSeconds(1), Duration.ofMillis(200), Duration.ofMillis(200));

    /** Ticks a placement may take on a live server before the test calls it a failure. */
    private static final int PLACEMENT_BUDGET = 200;

    /** Ticks GO may take to fire before the test calls it missing: comfortably past the longest hold. */
    private static final int GO_BUDGET = 1_000;

    /** The flying speed a client has when nothing has changed it: the default of Minecraft's creative flight. */
    private static final float DEFAULT_FLYING_SPEED = 0.05f;

    @TempDir
    Path tempDir;

    /** The first tick of the lobby begins the placement, and the racer is hovering from that tick, before any move. */
    @Test
    void aRacerWhosePlacementBeginsIsHoveringBeforeTheyAreMoved(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS)) {
            Player racer = cup.connectRacer();

            cup.tick();

            assertThat(racer.isFlying()).as("hovering on the first lobby tick, before the move").isTrue();
            assertThat(racer.isAllowFlying()).as("allowed to hover on the first lobby tick").isTrue();
            assertThat(racer.getFlyingSpeed()).as("flying speed while held").isZero();
        }
    }

    /** A held racer's own position packet is refused: they stay exactly where they are held. */
    @Test
    void aHeldRacersOwnPositionPacketDoesNotMoveThem(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS)) {
            Player racer = placedRacer(cup);
            Pos held = racer.getPosition();

            Vec step = new Vec(held.x() + 1.0, held.y(), held.z());
            clientSends(racer, new ClientPlayerPositionPacket(step, true, false));
            cup.tick();

            assertThat(racer.getPosition().distance(held))
                    .as("distance from the hold after the client sent a move")
                    .isCloseTo(0.0, within(1e-9));
        }
    }

    /** A held racer may turn their head: the rotation is accepted, and the racer does not move. */
    @Test
    void aHeldRacerCanTurnTheirHead(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS)) {
            Player racer = placedRacer(cup);
            Pos held = racer.getPosition();

            clientSends(racer, new ClientPlayerRotationPacket(90f, -20f, true, false));
            cup.tick();

            assertThat(racer.getPosition().yaw()).as("yaw after the head turned").isCloseTo(90f, within(0.5f));
            assertThat(racer.getPosition().pitch()).as("pitch after the head turned").isCloseTo(-20f, within(0.5f));
            assertThat(racer.getPosition().distance(held))
                    .as("distance from the hold after the head turned")
                    .isCloseTo(0.0, within(1e-9));
        }
    }

    /** A held racer's client asks to start a glide: the elytra is refused on the tick the request is read. */
    @Test
    void aHeldRacersClientCannotTurnTheElytraOn(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS)) {
            Player racer = placedRacer(cup);

            clientSends(racer, new ClientEntityActionPacket(racer.getEntityId(),
                    ClientEntityActionPacket.Action.START_FLYING_ELYTRA, 0));
            cup.tick();

            assertThat(racer.isFlyingWithElytra()).as("gliding after the client asked to glide").isFalse();
        }
    }

    /** A held racer who double-taps their flight off is hovering again on the tick after that. */
    @Test
    void aHeldRacerWhoLeavesTheirFlightIsHoveringAgainOnTheNextTick(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS)) {
            Player racer = placedRacer(cup);

            clientSends(racer, new ClientPlayerAbilitiesPacket((byte) 0));
            cup.tick();
            assertThat(racer.isFlying()).as("flying on the tick the client left flight").isFalse();
            cup.tick();

            assertThat(racer.isFlying()).as("flying again on the tick after").isTrue();
        }
    }

    /** The hold lasts until the tick GO fires, and the hover is taken away on that tick, as the client is told. */
    @Test
    void theHoverLastsUntilTheTickGoFiresAndIsTakenAwayOnThatTick(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS)) {
            Player racer = placedRacer(cup);
            Collector<PlayerAbilitiesPacket> abilities =
                    cup.connectionOf(racer).trackIncoming(PlayerAbilitiesPacket.class);

            tickUpToGo(cup, racer);

            assertThat(racer.isFlying()).as("flying on the tick GO fired").isFalse();
            assertThat(racer.isAllowFlying()).as("allowed to fly on the tick GO fired").isFalse();
            assertThat(racer.getFlyingSpeed()).as("flying speed on the tick GO fired").isEqualTo(DEFAULT_FLYING_SPEED);
            PlayerAbilitiesPacket last = abilities.collect().getLast();
            assertThat(last.flags() & PlayerAbilitiesPacket.FLAG_FLYING)
                    .as("the client is told it is not flying").isZero();
            assertThat(last.flyingSpeed()).as("the client is told the default flying speed")
                    .isEqualTo(DEFAULT_FLYING_SPEED);
        }
    }

    /** Once GO has released a racer, their own position packets move them again. */
    @Test
    void aRacerReleasedAtGoMovesWithTheirOwnClientAgain(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS)) {
            Player racer = placedRacer(cup);
            tickUpToGo(cup, racer);
            Pos before = racer.getPosition();

            Vec step = new Vec(before.x() + 0.5, before.y(), before.z());
            clientSends(racer, new ClientPlayerPositionPacket(step, true, false));
            cup.tick();

            assertThat(racer.getPosition().x())
                    .as("x after the client moved the racer on after GO")
                    .isCloseTo(before.x() + 0.5, within(1e-6));
        }
    }

    /**
     * A racer still loading when GO fires is held from the start of their placement, and is released on the tick their
     * placement completes, which is the tick their run begins.
     */
    @Test
    void aRacerStillLoadingWhenGoFiresIsReleasedOnTheTickTheyArePlaced(Env env) throws IOException {
        GatedChunkLoader loader = new GatedChunkLoader();
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS, loader)) {
            placedRacer(cup);
            loader.hold();
            Player late = cup.connectRacer();
            tickUpToGo(cup, late);
            assertThat(late.isFlying()).as("hovering after GO, while still loading").isTrue();

            loader.release();
            cup.tickUntil(() -> cup.runs.of(late.getUuid()).isPresent(), PLACEMENT_BUDGET,
                    "the late racer's run to begin once they are placed");

            assertThat(late.isFlying()).as("flying on the tick the late racer is placed").isFalse();
            assertThat(late.isAllowFlying()).as("allowed to fly on the tick the late racer is placed").isFalse();
        }
    }

    /** An abort puts every held racer's flight back as it was, and the racer's own flight is not left on. */
    @Test
    void anAbortPutsEveryHeldRacersFlightBackAsItWas(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS)) {
            Player racer = placedRacer(cup);
            assertThat(cup.session.situation(StartGate.COMMIT_WINDOW))
                    .as("the cup is committed, so it can be aborted")
                    .isEqualTo(StartGate.Situation.COMMITTED);

            cup.session.abort();

            assertThat(racer.isFlying()).as("flying after the abort").isFalse();
            assertThat(racer.isAllowFlying()).as("allowed to fly after the abort").isFalse();
            assertThat(racer.getFlyingSpeed()).as("flying speed after the abort").isEqualTo(DEFAULT_FLYING_SPEED);
        }
    }

    /** A disarm puts every held racer's flight back as it was, on the racer who is sent back to the lobby too. */
    @Test
    void aDisarmPutsEveryHeldRacersFlightBackAsItWas(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, OPEN_LOBBY)) {
            Player racer = placedRacer(cup);

            cup.session.disarm(cup.lobby, lobbySpot());

            assertThat(racer.isFlying()).as("flying after the disarm").isFalse();
            assertThat(racer.isAllowFlying()).as("allowed to fly after the disarm").isFalse();
            assertThat(racer.getFlyingSpeed()).as("flying speed after the disarm").isEqualTo(DEFAULT_FLYING_SPEED);
        }
    }

    /** A held racer who disconnects has their hover taken away, so nothing of it is left on the leaving racer. */
    @Test
    void aHeldRacerWhoDisconnectsHasTheirFlightPutBack(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS)) {
            Player racer = placedRacer(cup);

            cup.disconnect(racer);

            assertThat(racer.isFlying()).as("flying after the disconnect").isFalse();
            assertThat(racer.isAllowFlying()).as("allowed to fly after the disconnect").isFalse();
            assertThat(racer.getFlyingSpeed()).as("flying speed after the disconnect").isEqualTo(DEFAULT_FLYING_SPEED);
        }
    }

    /**
     * Places a racer on the first map, as {@link StartFreezeTest} does, and has their client confirm the teleport that
     * placed them, so that the packets the client sends afterwards are read.
     */
    private static Player placedRacer(PlacementFixture cup) {
        Player racer = cup.connectRacer();
        cup.tickUntil(() -> PlacementFixture.atSpawn(racer, cup.firstWorld, PlacementFixture.FIRST_SPAWN),
                PLACEMENT_BUDGET, "the racer's placement on the first map");
        clientSends(racer, new ClientTeleportConfirmPacket(racer.getLastSentTeleportId()));
        cup.tick();
        return racer;
    }

    /**
     * Ticks the cup until GO has fired, and asserts after every tick before it that {@code racer} is still hovering,
     * so a hover taken away early fails on the tick it happens. The tick that fires GO is not asserted here.
     */
    private static void tickUpToGo(PlacementFixture cup, Player racer) {
        for (int tick = 0; tick < GO_BUDGET; tick++) {
            cup.tick();
            if (cup.session.describe().contains("phase GAME")) {
                return;
            }
            assertThat(racer.isFlying()).as("hovering after lobby tick %s, before GO", tick).isTrue();
        }
        throw new AssertionError("GO did not fire within %s ticks".formatted(GO_BUDGET));
    }

    private static void clientSends(Player racer, ClientPacket packet) {
        racer.addPacketToQueue(packet);
    }

    private static Pos lobbySpot() {
        Vec3 spot = PlacementFixture.LOBBY_SPOT;
        return new Pos(spot.x(), spot.y(), spot.z());
    }
}
