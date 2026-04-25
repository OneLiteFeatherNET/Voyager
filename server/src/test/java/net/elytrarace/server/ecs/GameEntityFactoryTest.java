package net.elytrarace.server.ecs;

import net.elytrarace.common.game.mode.GameMode;
import net.elytrarace.server.cup.CupDefinition;
import net.elytrarace.server.cup.MapDefinition;
import net.elytrarace.server.ecs.component.ActiveMapComponent;
import net.elytrarace.server.ecs.component.CupProgressComponent;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.ecs.component.RingTrackerComponent;
import net.elytrarace.server.ecs.component.ScoreComponent;
import net.elytrarace.server.physics.Ring;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnvTest
@org.junit.jupiter.api.Disabled("Requires full Minestom player support - run manually")
class GameEntityFactoryTest {

    private static final Ring RING = new Ring(Vec.ZERO, new Vec(0, 0, 1), 3.0, 10);

    @Test
    void playerEntityHasAllRequiredComponents(Env env) {
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0, 42, 0));

        var entity = GameEntityFactory.createPlayerEntity(player);

        assertThat(entity.hasComponent(PlayerRefComponent.class)).isTrue();
        assertThat(entity.hasComponent(ElytraFlightComponent.class)).isTrue();
        assertThat(entity.hasComponent(RingTrackerComponent.class)).isTrue();
        assertThat(entity.hasComponent(ScoreComponent.class)).isTrue();
    }

    @Test
    void playerEntityReferencesCorrectPlayer(Env env) {
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0, 42, 0));

        var entity = GameEntityFactory.createPlayerEntity(player);

        var ref = entity.getComponent(PlayerRefComponent.class);
        assertThat(ref.getPlayerId()).isEqualTo(player.getUuid());
        assertThat(ref.getPlayer()).isSameAs(player);
    }

    @Test
    void playerEntityFlightIsInitiallyStationary(Env env) {
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0, 42, 0));

        var entity = GameEntityFactory.createPlayerEntity(player);

        var flight = entity.getComponent(ElytraFlightComponent.class);
        assertThat(flight.getVelocity()).isEqualTo(Vec.ZERO);
        assertThat(flight.isFlying()).isFalse();
    }

    @Test
    void gameEntityHasAllRequiredComponents() {
        var map = new MapDefinition("TestMap", Path.of("/tmp/test"), List.of(RING), new Pos(0, 60, 0));
        var cup = new CupDefinition("TestCup", GameMode.RACE, List.of(map));

        var entity = GameEntityFactory.createGameEntity(cup);

        assertThat(entity.hasComponent(CupProgressComponent.class)).isTrue();
        assertThat(entity.hasComponent(ActiveMapComponent.class)).isTrue();
    }

    @Test
    void gameEntityCupProgressStartsAtFirstMap() {
        var map1 = new MapDefinition("Map1", Path.of("/tmp/map1"), List.of(RING), new Pos(0, 60, 0));
        var map2 = new MapDefinition("Map2", Path.of("/tmp/map2"), List.of(RING), new Pos(0, 60, 0));
        var cup = new CupDefinition("TestCup", GameMode.RACE, List.of(map1, map2));

        var entity = GameEntityFactory.createGameEntity(cup);

        var progress = entity.getComponent(CupProgressComponent.class);
        assertThat(progress.getCurrentMapIndex()).isZero();
        assertThat(progress.isComplete()).isFalse();
        assertThat(progress.getCurrentMap().name()).isEqualTo("Map1");
    }

    @Test
    void playerEntityHasUniqueId(Env env) {
        var instance = env.createFlatInstance();
        var player1 = env.createPlayer(instance, new Pos(0, 42, 0));
        var player2 = env.createPlayer(instance, new Pos(5, 42, 5));

        var entity1 = GameEntityFactory.createPlayerEntity(player1);
        var entity2 = GameEntityFactory.createPlayerEntity(player2);

        assertThat(entity1.getId()).isNotEqualTo(entity2.getId());
    }
}
