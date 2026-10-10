package net.elytrarace.voyager.setup.adapter;

import static net.elytrarace.voyager.setup.adapter.AllowEveryone.ALLOW_ALL;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.platform.catalog.JsonDraftStore;
import net.elytrarace.voyager.platform.world.MapInstances;
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

/**
 * The builder's path end to end, on the setup server's own classes: create a map, place three rings, remove one, set
 * the spawn, read the status, and find a map the game server can load in maps/.
 */
@EnvTest
class BuilderWorkflowTest {

    @TempDir
    Path root;

    @Test
    void theBuildersPathEndsInAGameLoadableMapWithTwoRingsAndASpawn(Env env) throws IOException {
        Path data = Files.createDirectories(root.resolve("data"));
        Path worlds = Files.createDirectories(root.resolve("worlds"));
        JsonDraftStore store = new JsonDraftStore(data, worlds);
        Instance start = env.createFlatInstance();
        BuilderSessions sessions = new BuilderSessions();
        env.process().command().register(
                new SetupCommands(store, sessions, worlds, new MapInstances(env.process().instance(), worlds), ALLOW_ALL));
        new WandListener(sessions, ALLOW_ALL).register(env.process().eventHandler());
        new TerrainGuard(sessions).register(env.process().eventHandler());
        sessions.register(env.process().eventHandler());
        TestConnection connection = env.createConnection();
        Player builder = connection.connect(start, new Pos(0, 64, 0));
        Collector<SystemChatPacket> chat = connection.trackIncoming(SystemChatPacket.class);

        command(env, builder, sessions, "map new skyfortress");
        for (double z : List.of(0.0, 10.0, 20.0)) {
            moveTo(env, builder, z);
            env.process().eventHandler().call(new PlayerUseItemEvent(builder, PlayerHand.MAIN, Wand.item(), 0));
            env.tick();
        }
        moveTo(env, builder, 5.0);
        builder.setSneaking(true);
        env.process().eventHandler().call(new PlayerHandAnimationEvent(builder, PlayerHand.MAIN));
        env.tick();
        builder.setSneaking(false);
        command(env, builder, sessions, "map spawn");
        command(env, builder, sessions, "map status");

        assertThat(data.resolve("maps").resolve("skyfortress.json")).exists();
        assertThat(data.resolve("drafts").resolve("skyfortress.json")).doesNotExist();
        assertThat(store.load(new MapId("skyfortress")).rings()).hasSize(2);
        assertThat(store.load(new MapId("skyfortress")).rings()).extracting(ring -> ring.index())
                .containsExactly(0, 1);
        assertThat(store.load(new MapId("skyfortress")).spawn()).isNotNull();
        assertThat(keys(chat)).doesNotContain("voyager.setup.status.problem.spawn",
                "voyager.setup.status.problem.rings", "voyager.setup.save.failed");
    }

    private static void command(Env env, Player builder, BuilderSessions sessions, String command) {
        env.process().command().execute(builder, command);
        env.tick();
        BoundedTicks.awaitArrival(env, sessions, builder);
    }

    private static void moveTo(Env env, Player builder, double z) {
        builder.setView(0, 0);
        builder.teleport(new Pos(0, 64, z));
        env.tick();
        env.tick();
    }

    private static List<String> keys(Collector<SystemChatPacket> chat) {
        return chat.collect().stream()
                .map(SystemChatPacket::message)
                .filter(TranslatableComponent.class::isInstance)
                .map(component -> ((TranslatableComponent) component).key())
                .toList();
    }
}
