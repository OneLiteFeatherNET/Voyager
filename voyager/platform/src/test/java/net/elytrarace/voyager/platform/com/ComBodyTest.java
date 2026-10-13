package net.elytrarace.voyager.platform.com;

import net.elytrarace.voyager.platform.text.Messages;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.Instance;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The computer racer's body on a Minestom instance. Each test gets a fresh {@link Env} and drives its ticks explicitly,
 * so nothing here waits on the wall clock.
 */
@EnvTest
class ComBodyTest {

    private static final UUID ID = UUID.fromString("6f1c2d3e-4a5b-4c6d-8e7f-900112233445");

    @Test
    void aBodySpawnsInItsInstanceAtItsPosition(Env env) {
        Instance instance = flatInstance(env);
        Pos start = new Pos(4, 41, 9);
        ComBody body = new ComBody(ID, Messages.computerRacerName(1));

        body.spawn(instance, start).join();
        env.tick();

        assertThat(body.instance()).as("the body is in the instance it was spawned into").isSameAs(instance);
        assertThat(body.position()).as("the body stands where it was spawned").isEqualTo(start);
    }

    @Test
    void aTeleportMovesTheBodyOnTheNextTick(Env env) {
        Instance instance = flatInstance(env);
        ComBody body = new ComBody(ID, Messages.computerRacerName(1));
        body.spawn(instance, new Pos(4, 41, 9)).join();
        Pos target = new Pos(12, 41, 7); // inside chunk 0,0, the only chunk the fixture loads

        body.teleport(target);
        env.tick();

        assertThat(body.position()).as("the body has moved to the teleport target after one tick").isEqualTo(target);
    }

    @Test
    void aBodyStaysWhereItIsPlacedWhenNothingMovesIt(Env env) {
        Instance instance = flatInstance(env);
        ComBody body = new ComBody(ID, Messages.computerRacerName(1));
        body.spawn(instance, new Pos(4, 60, 9)).join();

        for (int tick = 0; tick < 17; tick++) {
            env.tick();
        }

        assertThat(body.position().y())
                .as("a finished body keeps the height it finished at: nothing pulls it down after the last move")
                .isEqualTo(60.0);
    }

    @Test
    void removalDespawnsTheBody(Env env) {
        Instance instance = flatInstance(env);
        ComBody body = new ComBody(ID, Messages.computerRacerName(1));
        body.spawn(instance, new Pos(4, 41, 9)).join();

        body.remove();
        env.tick();

        assertThat(body.isRemoved()).as("the body is removed once the tick that removes it has run").isTrue();
    }

    @Test
    void theBodyShowsItsCustomName(Env env) {
        Instance instance = flatInstance(env);
        ComBody body = new ComBody(ID, Messages.computerRacerName(1));
        body.spawn(instance, new Pos(4, 41, 9)).join();

        assertThat(body.customName())
                .as("the body carries the seat's name")
                .isEqualTo(Messages.computerRacerName(1));
        assertThat(body.isCustomNameVisible())
                .as("the name is drawn above the body's head")
                .isTrue();
    }

    private static Instance flatInstance(Env env) {
        Instance instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        return instance;
    }
}
