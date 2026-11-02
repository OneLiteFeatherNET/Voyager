package net.elytrarace.voyager.setup.adapter;

import net.elytrarace.voyager.api.mapsetup.DraftStore;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.permission.PermissionNode;
import net.elytrarace.voyager.api.permission.PermissionPolicy;
import net.elytrarace.voyager.api.permission.PermissionSubject;
import net.elytrarace.voyager.platform.catalog.JsonDraftStore;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.setup.mapsetup.DraftEditor;
import net.elytrarace.voyager.setup.mapsetup.EditClaims;
import net.kyori.adventure.text.TranslatableComponent;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.play.SystemChatPacket;
import net.minestom.server.network.player.GameProfile;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

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
        EditClaims claims = new EditClaims();
        BuilderSessions sessions = new BuilderSessions(claims);
        MapInstances instances = new MapInstances(env.process().instance(), worlds);
        PermissionPolicy refuseEveryone = (subject, node) -> false;
        env.process().command().register(
                new SetupCommands(new JsonDraftStore(data, worlds), sessions, worlds, instances, refuseEveryone, new SetupLobby(env.process().instance())));
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
    void aBuilderWithoutTheNodeTakesNoClaimWhenItOpensAMap(Env env) throws IOException {
        Path data = Files.createDirectories(root.resolve("data"));
        Path worlds = Files.createDirectories(root.resolve("worlds"));
        DraftStore store = new JsonDraftStore(data, worlds);
        store.create(DraftEditor.skeleton(new MapId("skyfortress")));
        Instance start = env.createFlatInstance();
        EditClaims claims = new EditClaims();
        BuilderSessions sessions = new BuilderSessions(claims);
        PermissionPolicy refuseEveryone = (subject, node) -> false;
        env.process().command().register(
                new SetupCommands(store, sessions, worlds, new MapInstances(env.process().instance(), worlds), refuseEveryone, new SetupLobby(env.process().instance())));
        TestConnection connection = env.createConnection();
        Player builder = connection.connect(start, new Pos(0, 64, 0));

        env.process().command().execute(builder, "map open skyfortress");
        env.tick();

        assertThat(sessions.find(builder.getUuid())).isEmpty();
        assertThat(claims.holder("map:skyfortress")).isEmpty();
    }

    @Test
    void aBuilderWithoutTheNodeCannotCloseTheMapItHolds(Env env) throws IOException {
        Fixture fixture = withdrawingAfterOpen(env);

        fixture.run("map close");

        assertThat(fixture.sessions().find(fixture.builder().getUuid())).isPresent();
        assertThat(fixture.claims().holder("map:skyfortress")).isPresent();
        assertThat(fixture.keys()).contains("voyager.command.denied");
    }

    @Test
    void aBuilderWithoutTheNodeCannotSpawnInTheMapItHolds(Env env) throws IOException {
        Fixture fixture = withdrawingAfterOpen(env);
        Path draft = data().resolve("drafts").resolve("skyfortress.json");
        byte[] before = Files.readAllBytes(draft);

        fixture.run("map spawn");

        assertThat(Files.readAllBytes(draft)).isEqualTo(before);
        assertThat(fixture.keys()).contains("voyager.command.denied");
    }

    @Test
    void aBuilderWithoutTheNodeCannotReadTheStatusOfTheMapItHolds(Env env) throws IOException {
        Fixture fixture = withdrawingAfterOpen(env);

        fixture.run("map status");

        assertThat(fixture.keys()).contains("voyager.command.denied");
        assertThat(fixture.keys()).doesNotContain("voyager.setup.status.rings");
    }

    /**
     * The unlock is gated by {@code voyager.setup.unlock} alone: Beta holds only that node, and Alpha holds the map.
     */
    @Test
    void unlockIsGatedByItsOwnNodeAloneSoABuilderWithoutTheUseNodeCanUnlock(Env env) throws IOException {
        Path data = Files.createDirectories(root.resolve("data"));
        Path worlds = Files.createDirectories(root.resolve("worlds"));
        Instance start = env.createFlatInstance();
        EditClaims claims = new EditClaims();
        BuilderSessions sessions = new BuilderSessions(claims);
        UUID alphaId = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        String use = PermissionNode.VOYAGER_SETUP_USE.value();
        String unlock = PermissionNode.VOYAGER_SETUP_UNLOCK.value();
        PermissionPolicy useForAlphaUnlockForAll = (subject, node) -> node.equals(unlock)
                || (node.equals(use) && subject instanceof PermissionSubject.Player player && player.id().equals(alphaId));
        env.process().command().register(new SetupCommands(new JsonDraftStore(data, worlds), sessions, worlds,
                new MapInstances(env.process().instance(), worlds), useForAlphaUnlockForAll, new SetupLobby(env.process().instance())));
        TestConnection alphaConnection = env.createConnection(new GameProfile(alphaId, "Alpha"));
        Player alpha = alphaConnection.connect(start, new Pos(0, 64, 0));
        TestConnection betaConnection =
                env.createConnection(new GameProfile(UUID.fromString("00000000-0000-0000-0000-00000000000b"), "Beta"));
        Player beta = betaConnection.connect(start, new Pos(0, 64, 0));
        Collector<SystemChatPacket> chat = betaConnection.trackIncoming(SystemChatPacket.class);
        env.process().command().execute(alpha, "map new skyfortress");
        env.tick();
        env.tick();

        env.process().command().execute(beta, "map unlock skyfortress");
        env.tick();

        assertThat(sessions.find(alphaId)).isEmpty();
        assertThat(claims.holder("map:skyfortress")).isEmpty();
        assertThat(chat.collect().stream().map(SystemChatPacket::message)
                .filter(TranslatableComponent.class::isInstance)
                .map(component -> ((TranslatableComponent) component).key())
                .toList()).doesNotContain("voyager.command.denied").contains("voyager.setup.map.unlocked");
    }

    private Path data() {
        return root.resolve("data");
    }

    /**
     * A builder with the node opens a map, then the node is withdrawn: every later command of the builder is refused.
     * The policy is a local flag, so each test has its own.
     */
    private Fixture withdrawingAfterOpen(Env env) throws IOException {
        Path data = Files.createDirectories(root.resolve("data"));
        Path worlds = Files.createDirectories(root.resolve("worlds"));
        Instance start = env.createFlatInstance();
        EditClaims claims = new EditClaims();
        BuilderSessions sessions = new BuilderSessions(claims);
        AtomicBoolean allowed = new AtomicBoolean(true);
        PermissionPolicy withdrawable = (subject, node) -> allowed.get();
        env.process().command().register(new SetupCommands(new JsonDraftStore(data, worlds), sessions, worlds,
                new MapInstances(env.process().instance(), worlds), withdrawable, new SetupLobby(env.process().instance())));
        TestConnection connection = env.createConnection();
        Player builder = connection.connect(start, new Pos(0, 64, 0));
        Collector<SystemChatPacket> chat = connection.trackIncoming(SystemChatPacket.class);
        env.process().command().execute(builder, "map new skyfortress");
        env.tick();
        env.tick();
        allowed.set(false);
        return new Fixture(env, builder, sessions, claims, chat);
    }

    private record Fixture(Env env, Player builder, BuilderSessions sessions, EditClaims claims,
            Collector<SystemChatPacket> chat) {

        void run(String command) {
            env.process().command().execute(builder, command);
            env.tick();
            env.tick();
        }

        List<String> keys() {
            return chat.collect().stream()
                    .map(SystemChatPacket::message)
                    .filter(TranslatableComponent.class::isInstance)
                    .map(component -> ((TranslatableComponent) component).key())
                    .toList();
        }
    }
}
