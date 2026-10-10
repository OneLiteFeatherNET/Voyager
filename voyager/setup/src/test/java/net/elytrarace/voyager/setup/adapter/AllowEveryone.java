package net.elytrarace.voyager.setup.adapter;

import net.elytrarace.voyager.api.permission.PermissionPolicy;

/**
 * A policy that allows every sender every node, for the setup tests that are about the map and the wand rather than
 * about permissions. The permission tests build their own refusing policy.
 */
final class AllowEveryone {

    static final PermissionPolicy ALLOW_ALL = (subject, node) -> true;

    private AllowEveryone() {
    }
}
