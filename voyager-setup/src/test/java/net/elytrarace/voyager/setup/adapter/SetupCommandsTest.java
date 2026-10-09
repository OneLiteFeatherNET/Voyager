package net.elytrarace.voyager.setup.adapter;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.platform.catalog.JsonDraftStore;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.elytrarace.voyager.platform.world.MapInstances;

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
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** The /map commands, each run through a fresh Minestom environment and driven tick by tick. */
@EnvTest
class SetupCommandsTest {

    @TempDir
    Path root;

    private Path data;
    private Path worlds;

    private void folders() throws IOException {
        data = Files.createDirectories(root.resolve("data"));
        worlds = Files.createDirectories(root.resolve("worlds"));
    }

    private Fixture fixture(Env env) throws IOException {
        folders();
        Instance start = env.createFlatInstance();
        BuilderSessions sessions = new BuilderSessions();
        MapInstances instances = new MapInstances(env.process().instance(), worlds);
        env.process().command().register(
                new SetupCommands(new JsonDraftStore(data, worlds), sessions, worlds, instances));
        TestConnection connection = env.createConnection();
        Player builder = connection.connect(start, new Pos(0, 64, 0));
        Collector<SystemChatPacket> chat = connection.trackIncoming(SystemChatPacket.class);
        return new Fixture(env, builder, sessions, chat);
    }

    @Test
    void newCreatesTheDraftAndTheWorldAndOpensTheMapForTheBuilder(Env env) throws IOException {
        Fixture fixture = fixture(env);

        fixture.run("map new skyfortress");

        assertThat(data.resolve("drafts").resolve("skyfortress.json")).exists();
        assertThat(worlds.resolve("skyfortress").resolve("region").resolve("r.0.0.mca")).exists();
        assertThat(fixture.sessions.find(fixture.builder.getUuid()).map(MapSession::id))
                .contains(new MapId("skyfortress"));
        assertThat(Wand.isHeldBy(fixture.builder)).isTrue();
    }

    @Test
    void newRefusesAnIdThatAlreadyHasADraftAndChangesNothing(Env env) throws IOException {
        Fixture fixture = fixture(env);
        fixture.run("map new skyfortress");
        byte[] before = Files.readAllBytes(data.resolve("drafts").resolve("skyfortress.json"));

        fixture.run("map new skyfortress");

        assertThat(Files.readAllBytes(data.resolve("drafts").resolve("skyfortress.json"))).isEqualTo(before);
        assertThat(fixture.keys()).contains("voyager.setup.map.exists");
    }

    @Test
    void newRefusesATraversalIdAndCreatesNothing(Env env) throws IOException {
        Fixture fixture = fixture(env);

        fixture.run("map new ../x");

        assertThat(listOf(data.resolve("drafts"))).isEmpty();
        assertThat(listOf(worlds)).isEmpty();
        assertThat(fixture.keys()).contains("voyager.setup.id.invalid");
    }

    @Test
    void openRefusesAMapThatDoesNotExist(Env env) throws IOException {
        Fixture fixture = fixture(env);

        fixture.run("map open missing");

        assertThat(fixture.keys()).contains("voyager.setup.refused");
        assertThat(fixture.sessions.find(fixture.builder.getUuid())).isEmpty();
    }

    @Test
    void spawnSavesTheSpawnAtTheBuildersFeet(Env env) throws IOException {
        Fixture fixture = fixture(env);
        fixture.run("map new skyfortress");

        fixture.run("map spawn");

        Vec3 feet = Vectors.toDomain(fixture.builder.getPosition());
        assertThat(new JsonDraftStore(data, worlds).load(new MapId("skyfortress")).spawn()).isEqualTo(feet);
    }

    @Test
    void statusNamesTheMissingSpawnAndTheMissingRings(Env env) throws IOException {
        Fixture fixture = fixture(env);
        fixture.run("map new skyfortress");

        fixture.run("map status");

        assertThat(fixture.keys()).contains("voyager.setup.status.spawn.unset", "voyager.setup.status.rings",
                "voyager.setup.status.problem.spawn", "voyager.setup.status.problem.rings");
    }

    @Test
    void statusWithoutAnOpenMapSaysSo(Env env) throws IOException {
        Fixture fixture = fixture(env);

        fixture.run("map status");

        assertThat(fixture.keys()).contains("voyager.setup.map.none");
    }

    private static List<Path> listOf(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.toList();
        }
    }

    /** The builder, the command path and what the builder was told. */
    private record Fixture(Env env, Player builder, BuilderSessions sessions, Collector<SystemChatPacket> chat) {

        void run(String command) {
            env.process().command().execute(builder, command);
            env.tick();
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
