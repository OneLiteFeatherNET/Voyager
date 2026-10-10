package net.elytrarace.voyager.platform.permission.luckperms;

/**
 * What a permission backend says about one node for one player, without naming the backend's own type. The adapter maps
 * LuckPerms' tri-state to this, so the gateway seam and its tests need no LuckPerms class.
 */
public enum NodeGrant {
    /** The node is granted. */
    TRUE,
    /** The node is explicitly denied. */
    FALSE,
    /** The node is not set for the player. Voyager treats it as a denial. */
    UNDEFINED
}
