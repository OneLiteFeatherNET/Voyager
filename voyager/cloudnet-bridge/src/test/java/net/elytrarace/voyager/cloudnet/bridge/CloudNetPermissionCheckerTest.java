package net.elytrarace.voyager.cloudnet.bridge;

import net.kyori.adventure.permission.PermissionChecker;
import net.kyori.adventure.pointer.Pointered;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.util.TriState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bridge answers CloudNet from the pointer a player carries, and nothing else. The fixtures are an Adventure
 * {@link Pointered} and a {@link PermissionChecker}, so the test needs neither a Minestom player nor a CloudNet type.
 */
class CloudNetPermissionCheckerTest {

    private static final String MAINTENANCE = "cloudnet.bridge.maintenance";

    @Test
    void aGrantedNodeIsAllowed() {
        Pointered player = withChecker(node -> TriState.TRUE);

        assertThat(CloudNetPermissionChecker.allows(player, MAINTENANCE)).isTrue();
    }

    @Test
    void aDeniedNodeIsRefused() {
        Pointered player = withChecker(node -> TriState.FALSE);

        assertThat(CloudNetPermissionChecker.allows(player, MAINTENANCE)).isFalse();
    }

    @Test
    void anUnsetNodeIsRefused() {
        Pointered player = withChecker(node -> TriState.NOT_SET);

        assertThat(CloudNetPermissionChecker.allows(player, MAINTENANCE))
                .as("only an explicit grant lets CloudNet's bypass through")
                .isFalse();
    }

    @Test
    void aPlayerWithoutThePointerIsRefused() {
        Pointered player = pointeredWith(Pointers.empty());

        assertThat(CloudNetPermissionChecker.allows(player, MAINTENANCE))
                .as("a player without the pointer must not pass a permission check it cannot answer")
                .isFalse();
    }

    @Test
    void theNodeNameReachesThePointerUnchanged() {
        List<String> asked = new ArrayList<>();
        Pointered player = withChecker(node -> {
            asked.add(node);
            return TriState.TRUE;
        });

        CloudNetPermissionChecker.allows(player, MAINTENANCE);

        assertThat(asked).containsExactly(MAINTENANCE);
    }

    private static Pointered withChecker(PermissionChecker checker) {
        return pointeredWith(Pointers.builder().withStatic(PermissionChecker.POINTER, checker).build());
    }

    private static Pointered pointeredWith(Pointers pointers) {
        return new Pointered() {
            @Override
            public Pointers pointers() {
                return pointers;
            }
        };
    }
}
