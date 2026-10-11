package net.elytrarace.voyager.platform.player;

import net.elytrarace.voyager.api.permission.PermissionPolicy;
import net.elytrarace.voyager.api.permission.PermissionSubject;
import net.kyori.adventure.permission.PermissionChecker;
import net.kyori.adventure.util.TriState;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every player of the server carries Adventure's permission pointer, and the pointer answers through the policy. The
 * policy is a fixture built inside each test, and the provider is installed on the test's own connection manager
 * before the player is created, so no test depends on another's player or provider.
 */
@EnvTest
class VoyagerPlayerTest {

    private static final String RELOAD = "voyager.command.race.reload";

    @Test
    void aPlayerCreatedByTheProviderCarriesThePermissionPointer(Env env) {
        Player player = playerWithPolicy(env, (subject, node) -> false);

        assertThat(player.get(PermissionChecker.POINTER))
                .as("the player's pointers must hold a PermissionChecker, or every CloudNet and command check answers false")
                .isPresent();
    }

    @Test
    void aGrantedNodeAnswersTrueThroughThePointer(Env env) {
        Player player = playerWithPolicy(env, (subject, node) -> RELOAD.equals(node));

        PermissionChecker checker = player.getOrDefault(PermissionChecker.POINTER, null);

        assertThat(checker.value(RELOAD)).isEqualTo(TriState.TRUE);
    }

    @Test
    void aDeniedNodeAnswersFalseThroughThePointer(Env env) {
        Player player = playerWithPolicy(env, (subject, node) -> false);

        PermissionChecker checker = player.getOrDefault(PermissionChecker.POINTER, null);

        assertThat(checker.value(RELOAD)).isEqualTo(TriState.FALSE);
    }

    @Test
    void thePolicyIsAskedAboutThisPlayersIdentity(Env env) {
        List<PermissionSubject> asked = new ArrayList<>();
        Player player = playerWithPolicy(env, (subject, node) -> {
            asked.add(subject);
            return true;
        });

        PermissionChecker checker = player.getOrDefault(PermissionChecker.POINTER, null);
        checker.value(RELOAD);

        assertThat(asked)
                .singleElement()
                .isEqualTo(new PermissionSubject.Player(player.getUuid(), player.getPermissionLevel()));
    }

    private static Player playerWithPolicy(Env env, PermissionPolicy policy) {
        MinecraftServer.getConnectionManager().setPlayerProvider(new VoyagerPlayerProvider(policy));
        Instance instance = env.createFlatInstance();
        return env.createPlayer(instance, new Pos(0, 40, 0));
    }
}
