package net.elytrarace.voyager.platform.permission.luckperms;

import me.lucko.luckperms.minestom.loader.MinestomLoader;

/**
 * The one class that names the loader. Kept apart from {@link LuckPermsBootstrap} so that resolving the loader happens only
 * when a start is requested, and a missing loader surfaces as a {@link LinkageError} the bootstrap can report.
 */
final class MinestomLoaderStart {

    private MinestomLoaderStart() {
    }

    static void run() {
        MinestomLoader.get().load().registerShutdownHook().start();
    }
}
