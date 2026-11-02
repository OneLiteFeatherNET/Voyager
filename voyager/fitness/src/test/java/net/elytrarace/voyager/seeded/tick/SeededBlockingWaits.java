package net.elytrarace.voyager.seeded.tick;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Seeded calls on a future for the tick-boundary rule. The rule must catch the blocking calls and let the non-blocking ones through.
 * Each class lives in the seeded package, which the architecture run never imports.
 */
public final class SeededBlockingWaits {

    private SeededBlockingWaits() {
    }

    public static final class JoinOnTheTick {
        public static void onTick(CompletableFuture<String> future) {
            future.join();
        }
    }

    public static final class GetOnTheTick {
        public static void onTick(Future<String> future) throws Exception {
            future.get();
        }
    }

    public static final class ThenRunOnTheTick {
        public static void onTick(CompletableFuture<Void> future) {
            future.thenRun(() -> { });
        }
    }

    public static final class WhenCompleteOnTheTick {
        public static void onTick(CompletableFuture<Void> future) {
            future.whenComplete((_, _) -> { });
        }
    }

    public static final class IsDoneOnTheTick {
        public static boolean onTick(CompletableFuture<?> future) {
            return future.isDone();
        }
    }
}
