package net.elytrarace.voyager.api.permission;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionPolicyFormsTest {

    private static final PermissionSubject CONSOLE = new PermissionSubject.Console();

    /** Records the node names the string form receives, and answers nothing else. */
    private static final class RecordingPolicy implements PermissionPolicy {
        private final List<String> asked = new ArrayList<>();

        @Override
        public boolean allows(PermissionSubject subject, String node) {
            asked.add(node);
            return true;
        }
    }

    @Test
    void theEnumFormPassesTheNodeValueToTheStringForm() {
        for (PermissionNode node : PermissionNode.values()) {
            RecordingPolicy policy = new RecordingPolicy();

            policy.allows(CONSOLE, node);

            assertThat(policy.asked)
                    .as("the enum form of %s must delegate with its value", node)
                    .containsExactly(node.value());
        }
    }

    @Test
    void thePlayerSubjectReachesTheStringFormUnchanged() {
        RecordingPolicy policy = new RecordingPolicy();
        PermissionSubject player = new PermissionSubject.Player(UUID.fromString("00000000-0000-0000-0000-0000000000aa"), 4);

        policy.allows(player, PermissionNode.VOYAGER_COMMAND_RACE_RELOAD);

        assertThat(policy.asked).containsExactly("voyager.command.race.reload");
    }
}
