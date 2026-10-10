package net.elytrarace.voyager.platform.convert;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.math.exception.NonFiniteVectorException;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnvTest
class VelocityExitTest {

    // Mutually distinct and not all positive, per the brief: a factor cannot be told from a
    // component swap on (1, 1, 1), and a lost sign does not show up when every component is
    // positive. Also chosen so no component happens to equal ServerFlag.SERVER_TICKS_PER_SECOND
    // (20) itself, which would make a "forgot to multiply" mutation on that one axis invisible.
    private static final Vec3 VELOCITY_PER_TICK = new Vec3(0.75, -0.32, 1.6);

    private Player createPlayer(Env env) {
        Instance instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        return env.createPlayer(instance, new Pos(0, 60, 0));
    }

    @Test
    void sendConvertsBlocksPerTickToBlocksPerSecondExactly(Env env) {
        Player player = createPlayer(env);
        double factor = ServerFlag.SERVER_TICKS_PER_SECOND;

        VelocityExit.send(player, VELOCITY_PER_TICK);

        assertThat(player.getVelocity())
                .isEqualTo(new Vec(0.75 * factor, -0.32 * factor, 1.6 * factor));
    }

    @Test
    void sendThrowsAndLeavesPlayerVelocityUntouchedWhenScaleOverflowsToInfinity(Env env) {
        Player player = createPlayer(env);
        // Finite on its own — Vec3's compact constructor accepts it — but multiplying by the tick
        // rate pushes it past Double.MAX_VALUE, so the boundary's re-check after the scale is what
        // has to catch this, not Vec3's constructor (already satisfied by construction).
        Vec3 tooLargeToScale = new Vec3(Double.MAX_VALUE / 2, 0.0, 0.0);
        Vec velocityBeforeCall = player.getVelocity();

        assertThatThrownBy(() -> VelocityExit.send(player, tooLargeToScale))
                .isInstanceOf(NonFiniteVectorException.class);

        assertThat(player.getVelocity()).isEqualTo(velocityBeforeCall);
    }

    // (0.75, -0.32, 1.6).scale(0.0) is (0.0, -0.0, 0.0): numerically zero, but not equal to an
    // all-positive-zero vector under record equality, which compares doubles with Double.compare
    // (-0.0 and 0.0 compare unequal there, unlike primitive ==). Asserted component-wise with
    // isZero(), which uses primitive == and treats -0.0 and 0.0 alike, instead of comparing whole
    // Vec objects and risking exactly that false negative.
    @Test
    void sendHandlesAVelocityScaledThroughNegativeZero(Env env) {
        Player player = createPlayer(env);
        Vec3 scaledToZero = VELOCITY_PER_TICK.scale(0.0);

        VelocityExit.send(player, scaledToZero);

        Vec result = player.getVelocity();
        assertThat(result.x()).isZero();
        assertThat(result.y()).isZero();
        assertThat(result.z()).isZero();
    }
}
