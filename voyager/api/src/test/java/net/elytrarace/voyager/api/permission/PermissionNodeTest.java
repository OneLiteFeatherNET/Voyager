package net.elytrarace.voyager.api.permission;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionNodeTest {

    @Test
    void holdsExactlyTheFiveNodesOfTheSpec() {
        Set<String> values = Arrays.stream(PermissionNode.values())
                .map(PermissionNode::value)
                .collect(Collectors.toSet());

        assertThat(values)
                .as("the node list is fixed by the command-permissions spec")
                .containsExactlyInAnyOrder(
                        "voyager.command.race.reload",
                        "voyager.command.race.start",
                        "voyager.command.race.skip",
                        "voyager.command.stop",
                        "voyager.setup.use");
    }

    @Test
    void everyNodeValueStartsWithTheVoyagerPrefix() {
        assertThat(PermissionNode.values())
                .extracting(PermissionNode::value)
                .allSatisfy(value -> assertThat(value).startsWith("voyager."));
    }

    @Test
    void nodeValuesAreUnique() {
        long distinct = Arrays.stream(PermissionNode.values())
                .map(PermissionNode::value)
                .distinct()
                .count();

        assertThat(distinct).isEqualTo(PermissionNode.values().length);
    }

    @Test
    void byValueFindsTheNodeWithTheSameValue() {
        assertThat(PermissionNode.byValue("voyager.command.stop")).isEqualTo(PermissionNode.VOYAGER_COMMAND_STOP);
    }

    @Test
    void byValueReturnsNullForAnUnknownValue() {
        assertThat(PermissionNode.byValue("cloudnet.bridge.maintenance")).isNull();
    }
}
