package net.elytrarace.voyager.platform.convert;

import net.elytrarace.voyager.api.math.Vec3;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@link VelocityExit} multiplies by {@link ServerFlag#SERVER_TICKS_PER_SECOND} and not a
 * literal {@code 20}.
 *
 * <p>{@code ServerFlag.SERVER_TICKS_PER_SECOND} is a {@code static final int} read once, from a
 * system property, the first time the class loads in a JVM — {@code System.setProperty} from
 * inside a test method is too late once anything else in that JVM has already touched
 * {@code ServerFlag}. So this class does not set the property itself; it only asserts that the
 * property already took effect. Setting it has to happen before this JVM's first line of code
 * runs, which only a forked test JVM's own startup arguments can guarantee — see the
 * {@code tpsFlagTest} task in {@code voyager/platform/build.gradle.kts}, the only task that runs
 * this class. The default {@code test} task excludes it, so every other test keeps the default
 * 20 TPS its own exactness assertions assume.
 */
@EnvTest
class VelocityExitCustomTpsTest {

    @Test
    void sendUsesTheConfiguredFlagRatherThanALiteral20(Env env) {
        assertThat(ServerFlag.SERVER_TICKS_PER_SECOND)
                .as("this test only proves anything against a non-default minestom.tps — see "
                        + "voyager/platform/build.gradle.kts' tpsFlagTest task")
                .isNotEqualTo(20);

        Instance instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        Player player = env.createPlayer(instance, new Pos(0, 60, 0));

        VelocityExit.send(player, new Vec3(1.0, 0.0, 0.0));

        assertThat(player.getVelocity())
                .isEqualTo(new Vec(ServerFlag.SERVER_TICKS_PER_SECOND, 0.0, 0.0));
    }
}
