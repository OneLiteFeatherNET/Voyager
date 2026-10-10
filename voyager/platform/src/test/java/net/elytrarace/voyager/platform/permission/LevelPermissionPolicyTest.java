package net.elytrarace.voyager.platform.permission;

import net.elytrarace.voyager.api.permission.PermissionNode;
import net.elytrarace.voyager.api.permission.PermissionSubject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LevelPermissionPolicyTest {

    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final String EXTERNAL_NODE = "cloudnet.bridge.maintenance";

    private final LevelPermissionPolicy policy = new LevelPermissionPolicy();

    @Test
    void theConsoleIsAllowedEveryVoyagerNode() {
        for (PermissionNode node : PermissionNode.values()) {
            assertThat(policy.allows(new PermissionSubject.Console(), node))
                    .as("the console must be allowed %s", node.value())
                    .isTrue();
        }
    }

    @Test
    void theConsoleIsAllowedAnExternalNodeName() {
        assertThat(policy.allows(new PermissionSubject.Console(), EXTERNAL_NODE)).isTrue();
    }

    @Test
    void aPlayerAtOperatorLevelFourIsAllowedEveryVoyagerNode() {
        PermissionSubject player = new PermissionSubject.Player(PLAYER_ID, 4);

        for (PermissionNode node : PermissionNode.values()) {
            assertThat(policy.allows(player, node))
                    .as("operator level 4 must be allowed %s", node.value())
                    .isTrue();
        }
    }

    @Test
    void aPlayerAtOperatorLevelFourIsAllowedAnExternalNodeName() {
        assertThat(policy.allows(new PermissionSubject.Player(PLAYER_ID, 4), EXTERNAL_NODE)).isTrue();
    }

    @Test
    void aPlayerAtOperatorLevelThreeIsDeniedEveryVoyagerNode() {
        PermissionSubject player = new PermissionSubject.Player(PLAYER_ID, 3);

        for (PermissionNode node : PermissionNode.values()) {
            assertThat(policy.allows(player, node))
                    .as("operator level 3 must be denied %s", node.value())
                    .isFalse();
        }
    }

    @Test
    void aPlayerAtOperatorLevelZeroIsDeniedEveryVoyagerNode() {
        PermissionSubject player = new PermissionSubject.Player(PLAYER_ID, 0);

        for (PermissionNode node : PermissionNode.values()) {
            assertThat(policy.allows(player, node))
                    .as("operator level 0 must be denied %s", node.value())
                    .isFalse();
        }
    }
}
