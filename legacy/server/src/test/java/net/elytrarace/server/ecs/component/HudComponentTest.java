package net.elytrarace.server.ecs.component;

import net.minestom.server.coordinate.Pos;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@EnvTest
class HudComponentTest {

    @Test
    void implementsComponent(Env env) {
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        var hud = new HudComponent(player);

        assertThat(hud).isInstanceOf(net.elytrarace.common.ecs.Component.class);
    }

    @Test
    void cleanupDoesNotThrowWhenNoBossBar(Env env) {
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        var hud = new HudComponent(player);

        // Must not throw even when no boss bar was ever shown
        hud.cleanup();
    }

    @Test
    void cleanupIsIdempotent(Env env) {
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        var hud = new HudComponent(player);
        hud.showCupProgress("TestCup", 1, 3);
        hud.cleanup();
        hud.cleanup(); // second call must not throw
    }

    @Test
    void canBeAddedToEntity(Env env) {
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        var entity = new net.elytrarace.common.ecs.Entity();
        entity.addComponent(new HudComponent(player));

        assertThat(entity.hasComponent(HudComponent.class)).isTrue();
        assertThat(entity.getComponent(HudComponent.class)).isNotNull();
    }
}
