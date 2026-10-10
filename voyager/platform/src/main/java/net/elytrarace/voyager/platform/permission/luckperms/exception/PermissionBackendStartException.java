package net.elytrarace.voyager.platform.permission.luckperms.exception;

/**
 * LuckPerms is on the class path but did not start, or its loader is missing when a start was requested. The composition
 * root logs the reason and exits without binding the port. It never falls back to the level-based policy in that case
 * (ADR-0024).
 */
public final class PermissionBackendStartException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PermissionBackendStartException(String message, Throwable cause) {
        super(message, cause);
    }
}
