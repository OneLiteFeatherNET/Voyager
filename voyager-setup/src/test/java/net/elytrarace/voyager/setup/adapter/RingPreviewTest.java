package net.elytrarace.voyager.setup.adapter;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.mapsetup.DraftStore;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.platform.catalog.JsonDraftStore;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.setup.mapsetup.DraftEditor;
import net.elytrarace.voyager.setup.mapsetup.RingDefaults;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerHandAnimationEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.instance.Instance;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** One display preview per ring of the open map, in the map's world, and none of them in the saved draft. */
@EnvTest
class RingPreviewTest {

    private static final MapId ID = new MapId("skyfortress");

    @TempDir
    Path root;

    private Path data;
    private Path worlds;

    @Test
    void openingAMapWithThreeRingsShowsThreePreviewsAtTheRingCenters(Env env) throws IOException {
        Fixture fixture = fixture(env);
        fixture.enter("map new skyfortress");
        fixture.seedRings(0, 10, 20);

        fixture.enter("map open skyfortress");

        fixture.awaitPreviews(3);
        List<Entity> displays = fixture.displays(fixture.world());
        assertThat(displays).hasSize(3);
        for (Ring ring : fixture.store.load(ID).rings()) {
            assertThat(displays).anySatisfy(display ->
                    assertThat(Vectors.toDomain(display.getPosition())).isEqualTo(ring.center()));
        }
    }

    @Test
    void aPlacedRingAddsOnePreviewAndARemovedRingRemovesOne(Env env) throws IOException {
        Fixture fixture = fixture(env);
        fixture.enter("map new skyfortress");

        fixture.rightClick();
        fixture.awaitPreviews(1);
        fixture.moveTo(new Pos(0, 64, -5));
        fixture.leftClick();

        fixture.awaitPreviews(0);
        assertThat(fixture.store.load(ID).rings()).isEmpty();
    }

    @Test
    void switchingMapsRemovesThePreviewsOfTheMapBeingLeft(Env env) throws IOException {
        Fixture fixture = fixture(env);
        fixture.enter("map new skyfortress");
        fixture.seedRings(0, 10);
        fixture.enter("map open skyfortress");
        fixture.awaitPreviews(2);
        Instance left = fixture.world();

        fixture.enter("map new canyon");

        assertThat(fixture.displays(left)).isEmpty();
    }

    @Test
    void aBuilderWhoDisconnectsTakesTheirPreviewsAndTheirSessionAway(Env env) throws IOException {
        Fixture fixture = fixture(env);
        fixture.enter("map new skyfortress");
        fixture.seedRings(0, 10);
        fixture.enter("map open skyfortress");
        fixture.awaitPreviews(2);
        Instance world = fixture.world();

        env.process().eventHandler().call(new PlayerDisconnectEvent(fixture.builder()));
        env.tick();

        assertThat(fixture.displays(world)).isEmpty();
        assertThat(fixture.sessions().find(fixture.builder().getUuid())).isEmpty();
    }

    private Fixture fixture(Env env) throws IOException {
        data = Files.createDirectories(root.resolve("data"));
        worlds = Files.createDirectories(root.resolve("worlds"));
        DraftStore store = new JsonDraftStore(data, worlds);
        Instance start = env.createFlatInstance();
        BuilderSessions sessions = new BuilderSessions();
        env.process().command().register(
                new SetupCommands(store, sessions, worlds, new MapInstances(env.process().instance(), worlds)));
        new WandListener(sessions).register(env.process().eventHandler());
        sessions.register(env.process().eventHandler());
        TestConnection connection = env.createConnection();
        Player builder = connection.connect(start, new Pos(0, 64, 0));
        return new Fixture(env, builder, store, sessions);
    }

    private record Fixture(Env env, Player builder, DraftStore store, BuilderSessions sessions) {

        void enter(String command) {
            env.process().command().execute(builder, command);
            env.tick();
            env.tickWhile(() -> sessions.find(builder.getUuid())
                    .map(session -> session.instance() != builder.getInstance())
                    .orElse(false), Duration.ofSeconds(10));
        }

        Instance world() {
            return builder.getInstance();
        }

        void moveTo(Pos position) {
            builder.setView(0, 0);
            builder.teleport(position);
            env.tick();
            env.tick();
        }

        void rightClick() {
            env.process().eventHandler().call(new PlayerUseItemEvent(builder, PlayerHand.MAIN, Wand.item(), 0));
            env.tick();
        }

        void leftClick() {
            env.process().eventHandler().call(new PlayerHandAnimationEvent(builder, PlayerHand.MAIN));
            env.tick();
        }

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

        List<Entity> displays(Instance instance) {
            return instance.getEntities().stream().filter(entity -> entity.getEntityType() == EntityType.BLOCK_DISPLAY)
                    .toList();
        }

        /** Ticks until the open map shows the given number of previews; the spawn is asynchronous. */
        void awaitPreviews(int count) {
            env.tickWhile(() -> displays(world()).size() != count, Duration.ofSeconds(10));
            assertThat(displays(world())).hasSize(count);
        }
    }
}
