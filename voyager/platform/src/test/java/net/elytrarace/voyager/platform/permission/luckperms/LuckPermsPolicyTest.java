package net.elytrarace.voyager.platform.permission.luckperms;

import net.elytrarace.voyager.api.permission.PermissionNode;
import net.elytrarace.voyager.api.permission.PermissionSubject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LuckPermsPolicyTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final String NODE = PermissionNode.VOYAGER_COMMAND_RACE_RELOAD.value();

    /** A gateway that answers from a fixed table; a missing entry stands for a player with no LuckPerms user. */
    private record FixedGateway(Map<String, NodeGrant> grants, boolean hasUser) implements LuckPermsGateway {
        @Override
        public Optional<NodeGrant> grant(UUID playerId, String node) {
            if (!hasUser) {
                return Optional.empty();
            }
            return Optional.of(grants.getOrDefault(node, NodeGrant.UNDEFINED));
        }
    }

    private static LuckPermsPolicy policyWith(FixedGateway gateway) {
        return new LuckPermsPolicy(gateway);
    }

    private static PermissionSubject player() {
        return new PermissionSubject.Player(PLAYER, 0);
    }

    @Test
    void aPlayerWithoutALuckPermsUserIsDeniedTheNode() {
        LuckPermsPolicy policy = policyWith(new FixedGateway(Map.of(NODE, NodeGrant.TRUE), false));

        assertThat(policy.allows(player(), NODE)).isFalse();
    }

    @Test
    void aPlayerWhoseUserHoldsTheNodeIsAllowed() {
        LuckPermsPolicy policy = policyWith(new FixedGateway(Map.of(NODE, NodeGrant.TRUE), true));

        assertThat(policy.allows(player(), NODE)).isTrue();
    }

    @Test
    void aNodeLeftUndefinedIsDenied() {
        LuckPermsPolicy policy = policyWith(new FixedGateway(Map.of(NODE, NodeGrant.UNDEFINED), true));

        assertThat(policy.allows(player(), NODE)).isFalse();
    }

    @Test
    void aNodeSetToFalseIsDenied() {
        LuckPermsPolicy policy = policyWith(new FixedGateway(Map.of(NODE, NodeGrant.FALSE), true));

        assertThat(policy.allows(player(), NODE)).isFalse();
    }

    @Test
    void theConsoleIsAllowedWhateverLuckPermsSays() {
        LuckPermsPolicy policy = policyWith(new FixedGateway(Map.of(), false));

        assertThat(policy.allows(new PermissionSubject.Console(), NODE)).isTrue();
    }
}
