package net.elytrarace.voyager.platform.lifecycle;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The one clean shutdown a server runs, whoever asks: the {@code stop} line on standard input, or {@code /stop}.
 *
 * <p>The stop action runs on the given executor, never on the caller's thread. The reader thread that asks is the one
 * the stop closes, so it cannot be the one that stops. A second request is ignored, so two stops never race. The
 * composition root supplies the action (stopping the game server, then exiting) and a platform-thread executor.
 */
public final class ServiceShutdown {

    /** The line a node or an operator writes on standard input to stop the server. */
    static final String STOP_LINE = "stop";

    private final Runnable stopAction;
    private final Executor executor;
    private final AtomicBoolean requested = new AtomicBoolean();

    /**
     * @param stopAction the shutdown itself, run at most once
     * @param executor   runs the shutdown on a thread other than the requester's
     */
    public ServiceShutdown(Runnable stopAction, Executor executor) {
        this.stopAction = Objects.requireNonNull(stopAction, "stopAction must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    /**
     * Asks for the shutdown. The first call submits the stop action; later calls do nothing.
     */
    public void request() {
        if (requested.compareAndSet(false, true)) {
            executor.execute(stopAction);
        }
    }

    /**
     * The console dispatcher with the {@code stop} line handled here. Every other line goes to {@code commands}, so
     * {@code end} and all other lines behave as they did before.
     *
     * @param commands runs every line that is not {@code stop}
     * @return a dispatcher for {@link ConsoleCommandReader}
     */
    public ConsoleCommandReader.Dispatcher consoleDispatcher(ConsoleCommandReader.Dispatcher commands) {
        return line -> {
            if (STOP_LINE.equals(line)) {
                request();
                return true;
            }
            return commands.dispatch(line);
        };
    }
}
