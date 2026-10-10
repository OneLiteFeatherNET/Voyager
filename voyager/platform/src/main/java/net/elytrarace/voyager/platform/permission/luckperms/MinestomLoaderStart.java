package net.elytrarace.voyager.platform.permission.luckperms;

import me.lucko.luckperms.common.loader.LoaderBootstrap;
import me.lucko.luckperms.minestom.loader.MinestomLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The one class that names the loader. It starts LuckPerms without the loader's own JVM shutdown hook and keeps the
 * bootstrap, so that {@link #stop()} can disable LuckPerms on the server's shutdown path.
 *
 * <p>Why not {@code registerShutdownHook()}: the hook runs at JVM exit, concurrently with H2's exit hook. H2 then
 * closes its database while LuckPerms' library class loader is already closed, and it prints
 * {@code NoClassDefFoundError: org/h2/api/ErrorCode}. Disabling LuckPerms inside the server's shutdown, before the JVM
 * runs its hooks, closes the database first, so H2 has nothing left to close at exit (ADR-0024).
 */
final class MinestomLoaderStart {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinestomLoaderStart.class);

    /** The bootstrap that {@link #run()} started, until {@link #stop()} disables it. */
    private static final AtomicReference<LoaderBootstrap> RUNNING = new AtomicReference<>();

    private MinestomLoaderStart() {
    }

    static void run() {
        MinestomLoader loader = MinestomLoader.get();
        loader.load().start();
        RUNNING.set(bootstrapOf(loader));
    }

    static void stop() {
        LoaderBootstrap bootstrap = RUNNING.getAndSet(null);
        if (bootstrap == null) {
            return;
        }
        try {
            bootstrap.onDisable();
        } catch (RuntimeException failure) {
            LOGGER.error("LuckPerms did not shut down cleanly", failure);
        }
    }

    /**
     * The loader keeps its bootstrap in a private field and offers no accessor. Reading the field is the only way to
     * disable LuckPerms without its JVM hook, and a LuckPerms release that renames the field fails the start, not the stop.
     */
    private static LoaderBootstrap bootstrapOf(MinestomLoader loader) {
        try {
            Field field = MinestomLoader.class.getDeclaredField("plugin");
            field.setAccessible(true);
            return (LoaderBootstrap) field.get(loader);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("the LuckPerms loader no longer exposes its bootstrap", failure);
        }
    }
}
