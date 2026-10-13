package net.elytrarace.voyager.cloudnet.bridge;

import net.kyori.adventure.permission.PermissionChecker;
import net.kyori.adventure.pointer.Pointered;
import net.kyori.adventure.util.TriState;
import org.jetbrains.annotations.ApiStatus;

/**
 * Answers CloudNet's permission questions from the pointer a Voyager player carries.
 *
 * <p>CloudNet's default checker reads the operator level, which a LuckPerms-managed server leaves at zero, so its
 * maintenance bypass would refuse every player, staff included. The pointer is the one the composition root installs
 * on every player (VoyagerPlayer in voyager-platform), and it resolves through the PermissionPolicy port. This class
 * holds no CloudNet type and no Voyager type: it reads Adventure's pointer, so it can be tested with a fake.
 */
@ApiStatus.Internal
public final class CloudNetPermissionChecker {

    private CloudNetPermissionChecker() {
    }

    /**
     * @param player the player CloudNet asks about; a Minestom player is one
     * @param node   the permission node, such as {@code cloudnet.bridge.maintenance}
     * @return {@code true} only when the player's pointer grants the node; {@code false} when it refuses it, leaves it
     *     unset, or the player carries no pointer
     */
    public static boolean allows(Pointered player, String node) {
        PermissionChecker checker = player.getOrDefault(PermissionChecker.POINTER, PermissionChecker.always(TriState.FALSE));
        return checker.value(node) == TriState.TRUE;
    }
}
