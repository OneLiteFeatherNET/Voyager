package net.elytrarace.voyager.platform.permission.luckperms;

import net.elytrarace.voyager.platform.permission.luckperms.exception.PermissionBackendStartException;

/**
 * Detects the optional LuckPerms loader and starts it. The loader is found by class name, without initialising it, so the
 * server compiles and runs without the jar. The caller checks {@link #isPresent()} first and calls {@link #start()} only
 * when it is true, before the network port binds.
 */
public final class LuckPermsBootstrap {

    private static final String LOADER_CLASS = "me.lucko.luckperms.minestom.loader.MinestomLoader";

    private LuckPermsBootstrap() {
    }

    /**
     * Whether the loader class is on the class path. The class is resolved, not initialised.
     *
     * @return {@code true} when the loader can be started
     */
    public static boolean isPresent() {
        try {
            Class.forName(LOADER_CLASS, false, LuckPermsBootstrap.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException _) {
            return false;
        }
    }

    /**
     * Starts LuckPerms. Call only when {@link #isPresent()} is true. LuckPerms is disabled by {@link #stop()}, which the
     * server runs in its own shutdown, not by a JVM shutdown hook (see {@link MinestomLoaderStart}).
     *
     * @throws PermissionBackendStartException when the loader is missing or fails to start; the cause is attached
     */
    public static void start() {
        try {
            MinestomLoaderStart.run();
        } catch (RuntimeException | LinkageError failure) {
            throw new PermissionBackendStartException("LuckPerms did not start: %s".formatted(failure), failure);
        }
    }

    /**
     * Disables LuckPerms, closing its storage and with it the H2 database. Idempotent, and a no-op when LuckPerms never
     * started, so a shutdown task may call it unconditionally. Must run before the JVM exits.
     */
    public static void stop() {
        MinestomLoaderStart.stop();
    }
}
