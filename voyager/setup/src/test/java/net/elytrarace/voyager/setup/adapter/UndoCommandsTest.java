package net.elytrarace.voyager.setup.adapter;

import static net.elytrarace.voyager.setup.adapter.AllowEveryone.ALLOW_ALL;
import net.elytrarace.voyager.api.mapsetup.DraftStore;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.mapsetup.exception.DraftWriteFailedException;
import net.elytrarace.voyager.api.race.effect.RingEffectTuning;
import net.elytrarace.voyager.platform.catalog.JsonDraftStore;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.setup.mapsetup.RingDefaults;
import net.kyori.adventure.text.TranslatableComponent;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
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

/**
 * {@code /map undo} and {@code /map redo} through the command path, each in a fresh Minestom environment. The draft
 * file is the observable result: an undo or redo that is not saved changes nothing a builder can see after a restart.
 */
@EnvTest
class UndoCommandsTest {

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
                new SetupCommands(store, sessions, worlds, new MapInstances(env.process().instance(), worlds), ALLOW_ALL));
        new WandListener(sessions, ALLOW_ALL).register(env.process().eventHandler());
        TestConnection connection = env.createConnection();
        Player builder = connection.connect(start, new Pos(0, 64, 0));
        Collector<SystemChatPacket> chat = connection.trackIncoming(SystemChatPacket.class);
        return new Fixture(env, builder, store, sessions, chat);
    }

    /** A fixture over a store in fresh folders, for the tests that need no failing save. */
    private Fixture plain(Env env) throws IOException {
        folders();
        return fixture(env, store());
    }

    private DraftStore store() {
        return new JsonDraftStore(data, worlds);
    }

    private Path draftFile() {
        return data.resolve("drafts").resolve("skyfortress.json");
    }

    @Test
    void undoAfterAPlacedRingSavesTheDraftWithoutIt(Env env) throws IOException {
        Fixture fixture = plain(env);
        fixture.enter("map new skyfortress");
        fixture.placeRingAt(0);
        fixture.placeRingAt(4);
        assertThat(store().load(ID).rings()).hasSize(2);

        fixture.run("map undo");

        assertThat(store().load(ID).rings()).hasSize(1);
        assertThat(fixture.sessions.find(fixture.builder.getUuid()).orElseThrow().draft().rings()).hasSize(1);
        assertThat(fixture.keys()).contains("voyager.setup.undo.done");
    }

    @Test
    void redoAfterAnUndoSavesThePlacedRingAgain(Env env) throws IOException {
        Fixture fixture = plain(env);
        fixture.enter("map new skyfortress");
        fixture.placeRingAt(0);
        fixture.placeRingAt(4);
        fixture.run("map undo");

        fixture.run("map redo");

        assertThat(store().load(ID).rings()).hasSize(2);
        assertThat(fixture.keys()).contains("voyager.setup.redo.done");
    }

    @Test
    void undoRightAfterOpeningAMapRepliesNoneAndWritesNothing(Env env) throws IOException {
        Fixture fixture = plain(env);
        fixture.enter("map new skyfortress");
        byte[] before = Files.readAllBytes(draftFile());

        fixture.run("map undo");

        assertThat(Files.readAllBytes(draftFile())).isEqualTo(before);
        assertThat(fixture.keys()).contains("voyager.setup.undo.none");
    }

    @Test
    void redoWithNothingUndoneRepliesNoneAndWritesNothing(Env env) throws IOException {
        Fixture fixture = plain(env);
        fixture.enter("map new skyfortress");
        fixture.placeRingAt(0);
        byte[] before = Files.readAllBytes(draftFile());

        fixture.run("map redo");

        assertThat(Files.readAllBytes(draftFile())).isEqualTo(before);
        assertThat(fixture.keys()).contains("voyager.setup.redo.none");
    }

    @Test
    void undoAfterASpawnRestoresTheSpawnAndKeepsTheDisplayName(Env env) throws IOException {
        Fixture fixture = plain(env);
        fixture.enter("map new skyfortress");
        MapDraft named = new MapDraft(ID, ID.value(), null, List.of(), RingDefaults.REFERENCE_TIME_SECONDS,
                RingDefaults.BOOST, RingDefaults.GUIDE_LINE, RingEffectTuning.DEFAULT, "Sky Fortress", List.of());
        store().save(named);
        fixture.enter("map open skyfortress");
        fixture.run("map spawn");
        assertThat(store().load(ID).spawn()).isNotNull();

        fixture.run("map undo");

        assertThat(store().load(ID).spawn()).isNull();
        assertThat(store().load(ID).displayName()).isEqualTo("Sky Fortress");
    }

    @Test
    void undoAfterOpeningAnotherMapAndComingBackRepliesNone(Env env) throws IOException {
        Fixture fixture = plain(env);
        fixture.enter("map new skyfortress");
        fixture.placeRingAt(0);
        fixture.enter("map new canyon");
        fixture.enter("map open skyfortress");
        byte[] before = Files.readAllBytes(draftFile());

        fixture.run("map undo");

        assertThat(Files.readAllBytes(draftFile())).isEqualTo(before);
        assertThat(fixture.keys()).contains("voyager.setup.undo.none");
    }

    @Test
    void aFailedUndoSaveKeepsTheDraftAndItsHistory(Env env) throws IOException {
        folders();
        FlakyStore flaky = new FlakyStore(store());
        Fixture fixture = fixture(env, flaky);
        fixture.enter("map new skyfortress");
        fixture.placeRingAt(0);
        byte[] before = Files.readAllBytes(draftFile());
        flaky.failSaves = true;

        fixture.run("map undo");

        assertThat(Files.readAllBytes(draftFile())).isEqualTo(before);
        assertThat(fixture.sessions.find(fixture.builder.getUuid()).orElseThrow().draft().rings()).hasSize(1);
        assertThat(fixture.keys()).contains("voyager.setup.save.failed").doesNotContain("voyager.setup.undo.done");
    }

    @Test
    void aFailedRedoSaveKeepsTheUndoneDraftUndone(Env env) throws IOException {
        folders();
        FlakyStore flaky = new FlakyStore(store());
        Fixture fixture = fixture(env, flaky);
        fixture.enter("map new skyfortress");
        fixture.placeRingAt(0);
        fixture.run("map undo");
        byte[] before = Files.readAllBytes(draftFile());
        flaky.failSaves = true;

        fixture.run("map redo");

        assertThat(Files.readAllBytes(draftFile())).isEqualTo(before);
        assertThat(fixture.sessions.find(fixture.builder.getUuid()).orElseThrow().draft().rings()).isEmpty();
        assertThat(fixture.keys()).contains("voyager.setup.save.failed").doesNotContain("voyager.setup.redo.done");
    }

    /** A store whose saves fail on demand, for the tests that need a failed write. */
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

    /** The builder, the command and event path, and what the builder was told. */
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
            BoundedTicks.awaitArrival(env, sessions, builder);
        }

        /** Stands the builder at {@code z} along the flight and right-clicks, which places a ring there. */
        void placeRingAt(double z) {
            builder.setView(0, 0);
            builder.teleport(new Pos(0, 64, z));
            env.tick();
            env.tick();
            env.process().eventHandler().call(new PlayerUseItemEvent(builder, PlayerHand.MAIN, Wand.item(), 0));
            env.tick();
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
