package net.elytrarace.voyager.setup.adapter;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.mapsetup.DraftStore;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.mapsetup.exception.DraftWriteFailedException;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.platform.catalog.JsonDraftStore;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.elytrarace.voyager.platform.text.SetupMessages;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.setup.mapsetup.DraftEditor;
import net.elytrarace.voyager.setup.mapsetup.RingDefaults;
import net.kyori.adventure.text.TranslatableComponent;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.player.PlayerHandAnimationEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.play.SystemChatPacket;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The wand: right-click places a ring from the builder's pose, left-click removes the ring the look ray crosses. */
@EnvTest
class WandListenerTest {

    private static final MapId ID = new MapId("skyfortress");

    @TempDir
    Path root;

    private Path data;
    private Path worlds;

    private void folders() throws IOException {
        data = Files.createDirectories(root.resolve("data"));
        worlds = Files.createDirectories(root.resolve("worlds"));
    }

    private Fixture fixture(Env env, DraftStore store) throws IOException {
        folders();
        Instance start = env.createFlatInstance();
        BuilderSessions sessions = new BuilderSessions();
        env.process().command().register(
                new SetupCommands(store, sessions, worlds, new MapInstances(env.process().instance(), worlds)));
        new WandListener(sessions).register(env.process().eventHandler());
        TestConnection connection = env.createConnection();
        Player builder = connection.connect(start, new Pos(0, 64, 0));
        Collector<SystemChatPacket> chat = connection.trackIncoming(SystemChatPacket.class);
        return new Fixture(env, builder, store, sessions, chat);
    }

    private DraftStore store() {
        return new JsonDraftStore(data, worlds);
    }

    @Test
    void threeRightClicksPlaceThreeRingsWithTheDefaultRadiusAndIndicesZeroOneTwo(Env env) throws IOException {
        folders();
        Fixture fixture = fixture(env, store());
        fixture.enter("map new skyfortress");

        for (int stand = 0; stand < 3; stand++) {
            fixture.moveTo(new Pos(stand * 4.0, 64, 0));
            fixture.rightClick();
        }

        List<Ring> rings = store().load(ID).rings();
        assertThat(rings).hasSize(3);
        assertThat(rings).extracting(Ring::index).containsExactly(0, 1, 2);
        assertThat(rings).allMatch(ring -> ring.radius() == RingDefaults.RADIUS);
        assertThat(rings.get(1).center()).isEqualTo(fixture.eyeAt(new Pos(4.0, 64, 0)));
    }

    @Test
    void aRingPlacedAfterTheSpawnIsSetMakesTheMapGameLoadable(Env env) throws IOException {
        folders();
        Fixture fixture = fixture(env, store());
        fixture.enter("map new skyfortress");
        fixture.run("map spawn");

        fixture.rightClick();

        assertThat(data.resolve("maps").resolve("skyfortress.json")).exists();
        assertThat(data.resolve("drafts").resolve("skyfortress.json")).doesNotExist();
    }

    @Test
    void aRightClickThatRepeatsTheLastRingsPoseIsTheSameClickAndPlacesNothing(Env env) throws IOException {
        folders();
        Fixture fixture = fixture(env, store());
        fixture.enter("map new skyfortress");

        fixture.rightClick();
        fixture.rightClick();

        assertThat(store().load(ID).rings()).hasSize(1);
    }

    @Test
    void aSneakLeftClickThroughTheMiddleOfThreeRingsRemovesItAndRenumbersTheLaterOne(Env env) throws IOException {
        folders();
        Fixture fixture = fixture(env, store());
        fixture.enter("map new skyfortress");
        fixture.seedRings(0, 10, 20);
        fixture.enter("map open skyfortress");
        fixture.moveTo(new Pos(0, 64, 5));

        fixture.sneakLeftClick();

        List<Ring> rings = store().load(ID).rings();
        assertThat(rings).extracting(Ring::index).containsExactly(0, 1);
        assertThat(rings.get(1).center().z()).isEqualTo(20.0);
    }

    @Test
    void aSneakLeftClickWithNoRingInReachChangesNothingAndSaysSo(Env env) throws IOException {
        folders();
        Fixture fixture = fixture(env, store());
        fixture.enter("map new skyfortress");
        fixture.seedRings(0, 10, 20);
        fixture.enter("map open skyfortress");
        fixture.moveTo(new Pos(0, 64, 5));
        fixture.lookDown();

        fixture.sneakLeftClick();

        assertThat(store().load(ID).rings()).hasSize(3);
        assertThat(fixture.keys()).contains("voyager.setup.ring.none");
    }

    @Test
    void aPlainLeftClickThroughARingChangesNothingAndSaysNothing(Env env) throws IOException {
        folders();
        Fixture fixture = fixture(env, store());
        fixture.enter("map new skyfortress");
        fixture.run("map spawn");
        fixture.seedRings(0, 10, 20);
        fixture.enter("map open skyfortress");
        fixture.moveTo(new Pos(0, 64, 5));
        byte[] before = Files.readAllBytes(data.resolve("maps").resolve("skyfortress.json"));

        fixture.leftClick();

        assertThat(Files.readAllBytes(data.resolve("maps").resolve("skyfortress.json"))).isEqualTo(before);
        assertThat(store().load(ID).rings()).hasSize(3);
        assertThat(fixture.keys()).doesNotContain("voyager.setup.ring.removed", "voyager.setup.ring.none");
    }

    @Test
    void aSaveThatFailsKeepsThePreviousDraftInMemoryAndInTheFile(Env env) throws IOException {
        folders();
        FlakyStore flaky = new FlakyStore(store());
        Fixture fixture = fixture(env, flaky);
        fixture.enter("map new skyfortress");
        byte[] before = Files.readAllBytes(data.resolve("drafts").resolve("skyfortress.json"));
        flaky.failSaves = true;

        fixture.rightClick();

        assertThat(Files.readAllBytes(data.resolve("drafts").resolve("skyfortress.json"))).isEqualTo(before);
        assertThat(fixture.sessions.find(fixture.builder.getUuid()).orElseThrow().draft().rings()).isEmpty();
        assertThat(fixture.keys()).contains("voyager.setup.save.failed");
    }

    /** A store whose saves fail on demand, for the one test that needs a failed write. */
    private static final class FlakyStore implements DraftStore {

        private final DraftStore delegate;
        private boolean failSaves;

        FlakyStore(DraftStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public MapDraft create(MapDraft skeleton) {
            return delegate.create(skeleton);
        }

        @Override
        public MapDraft load(MapId id) {
            return delegate.load(id);
        }

        @Override
        public void save(MapDraft draft) {
            if (failSaves) {
                throw DraftWriteFailedException.of(draft.id(), new IOException("the disk is full"));
            }
            delegate.save(draft);
        }
    }

    /** The builder, the event path and what the builder was told. */
    private record Fixture(Env env, Player builder, DraftStore store, BuilderSessions sessions,
            Collector<SystemChatPacket> chat) {

        void run(String command) {
            env.process().command().execute(builder, command);
            env.tick();
            env.tick();
        }

        /** Runs a command that opens a map, then ticks until the builder has arrived in that map's world. */
        void enter(String command) {
            run(command);
            BoundedTicks.tickWhile(env, () -> sessions.find(builder.getUuid())
                    .map(session -> session.instance() != builder.getInstance())
                    .orElse(false));
        }

        void moveTo(Pos position) {
            builder.setView(0, 0);
            builder.teleport(position);
            env.tick();
            env.tick();
        }

        void lookDown() {
            builder.setView(0, 90);
            env.tick();
        }

        Vec3 eyeAt(Pos feet) {
            return Vectors.toDomain(feet.add(0, builder.getEyeHeight(), 0));
        }

        void rightClick() {
            env.process().eventHandler().call(new PlayerUseItemEvent(builder, PlayerHand.MAIN, Wand.item(), 0));
            env.tick();
        }

        /** A left-click without sneaking: the swing reaches the server, and the wand does nothing to rings. */
        void leftClick() {
            env.process().eventHandler().call(new PlayerHandAnimationEvent(builder, PlayerHand.MAIN));
            env.tick();
        }

        /** The removal gesture of owner decision O2: sneak, then left-click. */
        void sneakLeftClick() {
            builder.setSneaking(true);
            env.tick();
            leftClick();
            builder.setSneaking(false);
            env.tick();
        }

        /** Three rings at the builder's eye height, facing along z, at the given z positions. */
        void seedRings(double... zs) {
            double eyeY = 64 + builder.getEyeHeight();
            MapDraft draft = store.load(ID);
            for (double z : zs) {
                draft = DraftEditor.withRingAppended(draft, new Ring(draft.rings().size(),
                        new Vec3(0, eyeY, z), new Vec3(0, 0, 1), RingDefaults.RADIUS, RingDefaults.POINTS,
                        RingType.STANDARD));
            }
            store.save(draft);
        }

        /** The translation keys of every chat line the builder has received so far. */
        List<String> keys() {
            return chat.collect().stream()
                    .map(SystemChatPacket::message)
                    .filter(TranslatableComponent.class::isInstance)
                    .map(component -> ((TranslatableComponent) component).key())
                    .toList();
        }
    }
}
