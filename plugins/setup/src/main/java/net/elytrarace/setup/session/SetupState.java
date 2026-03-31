package net.elytrarace.setup.session;

/**
 * Represents the current phase a builder is in during map setup.
 */
public enum SetupState {

    IDLE,
    MAP_SELECTED,
    PORTAL_PLACING,
    PORTAL_EDITING,
    TESTFLY_ACTIVE
}
