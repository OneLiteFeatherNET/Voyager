package net.elytrarace.voyager.platform.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The clean shutdown a node requests with {@code stop}. The stop action is a counter, never the real
 * {@code MinecraftServer.stopCleanly()}, and every thread the executor starts is joined before an assertion, so no
 * test waits on the clock.
 */
class ServiceShutdownTest {

    /** An executor that starts each task on a new platform thread and keeps the thread so a test can join it. */
    private static final class ThreadPerTask implements Executor {
        private final List<Thread> started = new ArrayList<>();

        @Override
        public void execute(Runnable task) {
            Thread thread = new Thread(task);
            started.add(thread);
            thread.start();
        }

        void awaitAll() throws InterruptedException {
            for (Thread thread : started) {
                thread.join();
            }
        }
    }

    @Test
    void aRequestRunsTheStopActionOnAThreadOtherThanTheCaller() throws InterruptedException {
        AtomicReference<Thread> stopThread = new AtomicReference<>();
        ThreadPerTask executor = new ThreadPerTask();
        ServiceShutdown shutdown = new ServiceShutdown(() -> stopThread.set(Thread.currentThread()), executor);

        shutdown.request();
        executor.awaitAll();

        assertThat(stopThread.get())
                .as("the stop action must not run on the thread that asked for it")
                .isNotNull()
                .isNotSameAs(Thread.currentThread());
    }

    @Test
    void twoRequestsRunTheStopActionOnce() throws InterruptedException {
        AtomicInteger stops = new AtomicInteger();
        ThreadPerTask executor = new ThreadPerTask();
        ServiceShutdown shutdown = new ServiceShutdown(stops::incrementAndGet, executor);

        shutdown.request();
        shutdown.request();
        executor.awaitAll();

        assertThat(stops).hasValue(1);
    }

    @Test
    void theStopLineRequestsShutdownAndIsNotPassedToTheCommands() {
        AtomicInteger stops = new AtomicInteger();
        List<String> passedOn = new ArrayList<>();
        ServiceShutdown shutdown = new ServiceShutdown(stops::incrementAndGet, Runnable::run);
        var dispatcher = shutdown.consoleDispatcher(line -> {
            passedOn.add(line);
            return true;
        });

        boolean matched = dispatcher.dispatch("stop");

        assertThat(matched).isTrue();
        assertThat(stops).hasValue(1);
        assertThat(passedOn).isEmpty();
    }

    @Test
    void theEndLineIsNotAShutdownAndGoesToTheCommands() {
        AtomicInteger stops = new AtomicInteger();
        List<String> passedOn = new ArrayList<>();
        ServiceShutdown shutdown = new ServiceShutdown(stops::incrementAndGet, Runnable::run);
        var dispatcher = shutdown.consoleDispatcher(line -> {
            passedOn.add(line);
            return false;
        });

        boolean matched = dispatcher.dispatch("end");

        assertThat(matched).isFalse();
        assertThat(passedOn).containsExactly("end");
        assertThat(stops).hasValue(0);
    }
}
