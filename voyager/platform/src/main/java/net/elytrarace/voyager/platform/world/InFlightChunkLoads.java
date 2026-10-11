package net.elytrarace.voyager.platform.world;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The chunk loads an instance has started and not yet finished.
 *
 * <p>Minestom caches a loaded chunk on a thread of its own, after the load future it hands out is already
 * underway, and it does not take the instance's monitor to do so. A world unregistered while one of those is
 * still running can therefore have a chunk inserted into, or expunged from, the map it is iterating. The
 * tracked future completes only after the chunk is cached, so waiting for it is what makes the unregister
 * safe.
 *
 * <p>Thread-safe. The set is the only state, and a load removes itself when it completes, whether it succeeds
 * or fails.
 */
final class InFlightChunkLoads {

    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();

    /**
     * Records a load as in flight until it completes.
     *
     * @param load the future a chunk load returned; it completes only after the chunk has been cached
     */
    void track(CompletableFuture<?> load) {
        pending.add(load);
        load.whenComplete((_, _) -> pending.remove(load));
    }

    /**
     * Waits until no tracked load is in flight, or until the bound passes.
     *
     * <p>A load that starts after the wait begins is waited for too, so the wait ends only at a moment when
     * nothing was in flight. A bound of zero returns at once, with the answer as it stands.
     *
     * @param bound the longest the caller is willing to wait
     * @return true if nothing was in flight when this returned, false if the bound passed first or the
     *         waiting thread was interrupted
     */
    boolean awaitQuiet(Duration bound) {
        long deadline = System.nanoTime() + bound.toNanos();
        while (true) {
            List<CompletableFuture<?>> snapshot = List.copyOf(pending);
            if (snapshot.isEmpty()) {
                return true;
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return false;
            }
            try {
                CompletableFuture.allOf(snapshot.toArray(CompletableFuture[]::new))
                        .get(remaining, TimeUnit.NANOSECONDS);
            } catch (TimeoutException exception) {
                return false;
            } catch (ExecutionException exception) {
                // A load that failed is finished all the same, and is dropped below like any other.
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
            // The completion callback that removes a load may run after this thread has been woken, so a
            // finished load is dropped here too. Otherwise the loop would spin on a load that is already done.
            snapshot.stream().filter(CompletableFuture::isDone).forEach(pending::remove);
        }
    }
}
