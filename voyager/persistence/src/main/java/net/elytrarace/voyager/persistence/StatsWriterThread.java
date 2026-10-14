package net.elytrarace.voyager.persistence;

/**
 * The platform thread that drives a {@link StatsWriter}. It calls {@link StatsWriter#step()} in a loop and pauses
 * briefly when there is nothing to write or a retry is waiting. The thread is a daemon, so it never keeps the
 * server alive.
 */
public final class StatsWriterThread {

    /** The name of the thread, as it appears in dumps and logs. */
    public static final String THREAD_NAME = "voyager-db-write";

    private static final long IDLE_MILLIS = 50;

    private final StatsWriter writer;
    private volatile boolean running = true;

    /**
     * @param writer the writer this thread drives
     */
    public StatsWriterThread(StatsWriter writer) {
        this.writer = writer;
    }

    /**
     * Starts the daemon thread.
     *
     * @return the started thread
     */
    public Thread start() {
        Thread thread = new Thread(this::run, THREAD_NAME);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /** Asks the loop to end after its current pass. The writer's drain is the caller's business. */
    public void requestStop() {
        running = false;
    }

    private void run() {
        while (running) {
            if (!writer.step()) {
                try {
                    Thread.sleep(IDLE_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
