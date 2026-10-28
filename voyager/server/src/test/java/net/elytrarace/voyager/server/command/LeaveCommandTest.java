package net.elytrarace.voyager.server.command;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;
import net.elytrarace.voyager.platform.catalog.LoadedCatalog;
import net.elytrarace.voyager.platform.collision.MinestomCollisionSpace;
import net.elytrarace.voyager.platform.cup.CupSession;
import net.elytrarace.voyager.platform.cup.LivePlayerSampler;
import net.elytrarace.voyager.platform.cup.MapTransition;
import net.elytrarace.voyager.platform.cup.RaceRuns;
import net.elytrarace.voyager.platform.flight.FireworkBoostTracker;
import net.elytrarace.voyager.platform.flight.FlightTracker;
import net.elytrarace.voyager.platform.handoff.HubHandoff;
import net.elytrarace.voyager.platform.lobby.LobbyHud;
import net.elytrarace.voyager.platform.lobby.LobbySidebar;
import net.elytrarace.voyager.platform.lobby.WaitingRoom;
import net.elytrarace.voyager.platform.stats.NoOpRaceRecorder;
import net.elytrarace.voyager.platform.text.Messages;
import net.elytrarace.voyager.platform.tick.FlightTickDriver;
import net.elytrarace.voyager.platform.world.CurrentMapBlocks;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.play.SystemChatPacket;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code /leave} and {@code /hub} on a Minestom test environment: a racer waiting in the lobby is handed to the hub through
 * the installed handoff, and a racer who stands anywhere else is refused and stays where they are. The waiting room is the
 * real one, built on the wiring the server uses, and no tick runs: a leave is decided on the spot.
 */
@EnvTest
class LeaveCommandTest {

    private static final Vec3 SPAWN = new Vec3(3.5, 65.0, 5.5);
    private static final MapDefinition RUN = new MapDefinition("leave-run", "leave_world", SPAWN,
            List.of(new Ring(0, SPAWN.plus(new Vec3(0, 8, 10)), new Vec3(0, 0, 1), 6.0, 7, RingType.STANDARD)),
            Duration.ofSeconds(60), new BoostConfig(4, 9), new GuideLine(List.of(), 2, 1.0));
    private static final CupDefinition CUP = new CupDefinition("leave_cup", List.of("leave-run"), GameMode.RACE);
    private static final RaceTimings TIMINGS = new RaceTimings(
            Duration.ofSeconds(10), Duration.ofSeconds(5), Duration.ofMillis(100), Duration.ofMillis(100));
    private static final Pos AT = new Pos(0, 42, 0);

    @TempDir
    Path tempDir;

    @Test
    void leaveSendsAWaitingRacerToTheHub(Env env) {
        Harness harness = new Harness(env);
        Racer racer = harness.connect(harness.waitingPlace);

        env.process().command().execute(racer.player(), "leave");

        assertThat(harness.handedToHub)
                .as("the racer waiting in the lobby is handed to the hub")
                .containsExactly(racer.player().getUuid());
    }

    @Test
    void hubIsAnAliasOfLeave(Env env) {
        Harness harness = new Harness(env);
        Racer racer = harness.connect(harness.waitingPlace);

        env.process().command().execute(racer.player(), "hub");

        assertThat(harness.handedToHub)
                .as("/hub hands the racer waiting in the lobby to the hub, as /leave does")
                .containsExactly(racer.player().getUuid());
    }

    @Test
    void aRacerWhoIsNotWaitingIsRefusedAndNotHandedToTheHub(Env env) {
        Harness harness = new Harness(env);
        Racer racer = harness.connect(harness.elsewhere);

        env.process().command().execute(racer.player(), "leave");

        assertThat(harness.handedToHub)
                .as("a racer outside the waiting place is not handed to the hub")
                .isEmpty();
        assertThat(racer.messages())
                .as("the racer is told the leave was refused")
                .contains(Messages.leaveRefused());
    }

    /** A connected racer and the chat the server sends to them. */
    private record Racer(Player player, Collector<SystemChatPacket> chat) {

        List<Component> messages() {
            return chat.collect().stream().map(SystemChatPacket::message).toList();
        }
    }

    /** The waiting room on the server's wiring, with the hub recording every racer it is asked to move. */
    private final class Harness {

        private final Env env;
        private final Instance waitingPlace;
        private final Instance elsewhere;
        private final List<UUID> handedToHub = new ArrayList<>();
        private final List<Player> online = new ArrayList<>();

        Harness(Env env) {
            this.env = env;
            this.waitingPlace = env.createFlatInstance();
            this.elsewhere = env.createFlatInstance();
            CatalogHolder catalog = new CatalogHolder(new LoadedCatalog(
                    new CatalogSnapshot(Map.of("leave-run", RUN), Map.of(CUP.key().asString(), CUP)), CUP, Instant.EPOCH));
            MapInstances instances = new MapInstances(env.process().instance(), tempDir.resolve("worlds"));
            RaceRuns runs = new RaceRuns();
            CurrentMapBlocks blocks = new CurrentMapBlocks();
            FireworkBoostTracker boosts = new FireworkBoostTracker();
            FlightTickDriver flight = new FlightTickDriver(
                    new LivePlayerSampler(() -> online, boosts), new FlightTracker(), new MinestomCollisionSpace(blocks));
            CupSession session = new CupSession(catalog, instances, new MapTransition(instances, runs), runs, flight,
                    blocks, boosts, TIMINGS, Duration.ofMillis(50), () -> online, new NoOpRaceRecorder(),
                    Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
            // The handoff is installed, so the fallback disconnect and the refusal warning are never reached.
            HubHandoff hub = new HubHandoff(() -> Optional.of(handedToHub::add), (racer, reason) -> { }, warning -> { });
            WaitingRoom room = new WaitingRoom(session, catalog, () -> online, 2, 16, null, hub, new LobbyHud(),
                    new LobbySidebar(), null, () -> waitingPlace);
            env.process().command().register(new LeaveCommand(room));
        }

        /** Connects a racer to the instance, with the chat the server sends them collected from the connection. */
        Racer connect(Instance instance) {
            TestConnection connection = env.createConnection();
            Collector<SystemChatPacket> chat = connection.trackIncoming(SystemChatPacket.class);
            Player player = connection.connect(instance, AT);
            online.add(player);
            return new Racer(player, chat);
        }
    }
}
