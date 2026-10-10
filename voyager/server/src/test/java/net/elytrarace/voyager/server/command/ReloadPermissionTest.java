package net.elytrarace.voyager.server.command;

import net.minestom.server.command.ConsoleSender;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Who may run {@code /race reload}. Minestom 26.2 has no named permissions, so the gate is the console, or a
 * player at the operator level.
 */
@EnvTest
class ReloadPermissionTest {

    @Test
    void theConsoleMayReload() {
        assertThat(ReloadPermission.mayReload(new ConsoleSender())).isTrue();
    }

    @Test
    void aPlayerWithoutAnyOperatorLevelMayNotReload(Env env) {
        Player player = connect(env);
        player.setPermissionLevel(0);

        assertThat(ReloadPermission.mayReload(player)).isFalse();
    }

    @Test
    void aPlayerBelowTheOperatorLevelMayNotReload(Env env) {
        Player player = connect(env);
        player.setPermissionLevel(ReloadPermission.REQUIRED_LEVEL - 1);

        assertThat(ReloadPermission.mayReload(player)).isFalse();
    }

    @Test
    void aPlayerAtTheOperatorLevelMayReload(Env env) {
        Player player = connect(env);
        player.setPermissionLevel(ReloadPermission.REQUIRED_LEVEL);

        assertThat(ReloadPermission.mayReload(player)).isTrue();
    }

    private static Player connect(Env env) {
        Instance instance = env.createFlatInstance();
        return env.createConnection().connect(instance, new Pos(0, 42, 0));
    }
}
