package net.elytrarace.voyager.setup.adapter;

import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.platform.catalog.JsonDraftStore;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerStartDiggingEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.PlayerHand;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Terrain is built in an external editor, so a block edit by a builder in an open map is cancelled. */
@EnvTest
class TerrainGuardTest {

    @TempDir
    Path root;

    @Test
    void aBlockBreakByABuilderInAnOpenMapIsCancelled(Env env) throws IOException {
        Fixture fixture = fixture(env);
        fixture.enter("map new skyfortress");
        PlayerBlockBreakEvent event = new PlayerBlockBreakEvent(fixture.builder, fixture.world(), Block.STONE,
                Block.AIR, new BlockVec(0, 60, 0), BlockFace.TOP);

        env.process().eventHandler().call(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void aBlockDigStartByABuilderInAnOpenMapIsCancelled(Env env) throws IOException {
        Fixture fixture = fixture(env);
        fixture.enter("map new skyfortress");
        PlayerStartDiggingEvent event = new PlayerStartDiggingEvent(fixture.builder, fixture.world(), Block.STONE,
                new BlockVec(0, 60, 0), BlockFace.TOP);

        env.process().eventHandler().call(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void aBlockPlacedByABuilderInAnOpenMapIsCancelled(Env env) throws IOException {
        Fixture fixture = fixture(env);
        fixture.enter("map new skyfortress");
        PlayerBlockPlaceEvent event = new PlayerBlockPlaceEvent(fixture.builder, fixture.world(), Block.STONE,
                BlockFace.TOP, new BlockVec(0, 60, 0), new Vec(0.5, 1, 0.5), PlayerHand.MAIN);

        env.process().eventHandler().call(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void aBlockBreakByABuilderWithNoOpenMapIsLeftAlone(Env env) throws IOException {
        Fixture fixture = fixture(env);
        PlayerBlockBreakEvent event = new PlayerBlockBreakEvent(fixture.builder, fixture.world(), Block.STONE,
                Block.AIR, new BlockVec(0, 60, 0), BlockFace.TOP);

        env.process().eventHandler().call(event);

        assertThat(event.isCancelled()).isFalse();
    }

    private Fixture fixture(Env env) throws IOException {
        Path data = Files.createDirectories(root.resolve("data"));
        Path worlds = Files.createDirectories(root.resolve("worlds"));
        Instance start = env.createFlatInstance();
        BuilderSessions sessions = new BuilderSessions();
        env.process().command().register(new SetupCommands(new JsonDraftStore(data, worlds), sessions, worlds,
                new MapInstances(env.process().instance(), worlds)));
        new TerrainGuard(sessions).register(env.process().eventHandler());
        TestConnection connection = env.createConnection();
        Player builder = connection.connect(start, new Pos(0, 64, 0));
        return new Fixture(env, builder, sessions);
    }

    private record Fixture(Env env, Player builder, BuilderSessions sessions) {

        void enter(String command) {
            env.process().command().execute(builder, command);
            env.tick();
            BoundedTicks.tickWhile(env, () -> sessions.find(builder.getUuid())
                    .map(session -> session.instance() != builder.getInstance())
                    .orElse(false));
        }

        Instance world() {
            return builder.getInstance();
        }
    }
}
