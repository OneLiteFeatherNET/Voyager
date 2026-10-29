package net.elytrarace.voyager.setup.adapter;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.mapsetup.DraftStore;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.permission.PermissionPolicy;
import net.elytrarace.voyager.platform.catalog.JsonDraftStore;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.setup.mapsetup.DraftEditor;
import net.kyori.adventure.text.TranslatableComponent;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The setup commands behind {@code voyager.setup.use}: a builder the policy refuses is told so, and nothing on disk
 * or in the builder's session changes.
 */
@EnvTest
class SetupPermissionTest {

    @TempDir
    Path root;

    @Test
    void aBuilderWithoutTheNodeCreatesNoDraftAndGetsNoWand(Env env) throws IOException {
        Path data = Files.createDirectories(root.resolve("data"));
        Path worlds = Files.createDirectories(root.resolve("worlds"));
        Instance start = env.createFlatInstance();
        BuilderSessions sessions = new BuilderSessions();
        MapInstances instances = new MapInstances(env.process().instance(), worlds);
        PermissionPolicy refuseEveryone = (subject, node) -> false;
        env.process().command().register(
                new SetupCommands(new JsonDraftStore(data, worlds), sessions, worlds, instances, refuseEveryone));
        TestConnection connection = env.createConnection();
        Player builder = connection.connect(start, new Pos(0, 64, 0));
        Collector<SystemChatPacket> chat = connection.trackIncoming(SystemChatPacket.class);

        env.process().command().execute(builder, "map new skyfortress");
        env.tick();

        assertThat(data.resolve("drafts").resolve("skyfortress.json")).doesNotExist();
        assertThat(sessions.find(builder.getUuid())).isEmpty();
        assertThat(Wand.isHeldBy(builder)).isFalse();
        assertThat(chat.collect().stream()
                .map(SystemChatPacket::message)
                .filter(TranslatableComponent.class::isInstance)
                .map(component -> ((TranslatableComponent) component).key()))
                .contains("voyager.command.denied");
    }

    @Test
    void aBuilderWithoutTheNodeCannotUndoOrRedoAndTheDraftKeepsItsBytes(Env env) throws IOException {
        Path data = Files.createDirectories(root.resolve("data"));
        Path worlds = Files.createDirectories(root.resolve("worlds"));
        DraftStore store = new JsonDraftStore(data, worlds);
        MapId id = new MapId("skyfortress");
        store.create(DraftEditor.skeleton(id));
        Instance start = env.createFlatInstance();
        BuilderSessions sessions = new BuilderSessions();
        MapInstances instances = new MapInstances(env.process().instance(), worlds);
        PermissionPolicy refuseEveryone = (subject, node) -> false;
        env.process().command().register(
                new SetupCommands(store, sessions, worlds, instances, refuseEveryone));
        TestConnection connection = env.createConnection();
        Player builder = connection.connect(start, new Pos(0, 64, 0));
        Collector<SystemChatPacket> chat = connection.trackIncoming(SystemChatPacket.class);
        MapSession session = new MapSession(store, store.load(id), start);
        sessions.open(builder.getUuid(), session);
        session.commit(DraftEditor.withSpawn(session.draft(), new Vec3(1, 64, 1)));
        Path draftFile = data.resolve("drafts").resolve("skyfortress.json");
        byte[] before = Files.readAllBytes(draftFile);

        env.process().command().execute(builder, "map undo");
        env.tick();
        env.process().command().execute(builder, "map redo");
        env.tick();

        assertThat(Files.readAllBytes(draftFile)).isEqualTo(before);
        assertThat(sessions.find(builder.getUuid()).orElseThrow().draft().spawn()).isEqualTo(new Vec3(1, 64, 1));
        assertThat(chat.collect().stream()
                .map(SystemChatPacket::message)
                .filter(TranslatableComponent.class::isInstance)
                .map(component -> ((TranslatableComponent) component).key()))
                .contains("voyager.command.denied")
                .doesNotContain("voyager.setup.undo.done", "voyager.setup.redo.done");
    }
}
