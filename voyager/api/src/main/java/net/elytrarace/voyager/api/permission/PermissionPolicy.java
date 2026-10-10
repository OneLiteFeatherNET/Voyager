package net.elytrarace.voyager.api.permission;

/**
 * The port every gated command and listener asks. Implementations live in voyager-platform: a LuckPerms adapter when
 * LuckPerms runs, and a level-based fallback when it does not. Nothing here names a backend.
 *
 * <p>Deliberately not sealed: the implementations sit in two modules, and a sealed hierarchy would force them into
 * one file.
 */
public interface PermissionPolicy {

    /**
     * Asks about a node name that Voyager may not own, such as the CloudNet bridge's {@code cloudnet.bridge.maintenance}.
     *
     * @param subject who asks
     * @param node    the node name to check
     * @return {@code true} when the subject may act on the node
     */
    boolean allows(PermissionSubject subject, String node);

    /**
     * Asks about one of Voyager's own nodes. Delegates to the name form with the node's value, so both forms answer alike.
     *
     * @param subject who asks
     * @param node    the node to check
     * @return {@code true} when the subject may act on the node
     */
    default boolean allows(PermissionSubject subject, PermissionNode node) {
        return allows(subject, node.value());
    }
}
