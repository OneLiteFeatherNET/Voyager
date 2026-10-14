package net.elytrarace.voyager.platform.stats;

import net.elytrarace.voyager.platform.text.Messages;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Answers a statistics read without the game loop waiting for it.
 *
 * <p>A command asks with {@link #ask}. The read runs on the reader pool, and its result waits in a bounded queue. The tick
 * calls {@link #tick}, which takes the new requests in, answers every request whose deadline has passed, and then sends the
 * results it finds, at most {@link #REPLIES_PER_TICK} lines per tick. So every chat line a read produces is sent from the
 * tick, never from the reader thread.
 *
 * <p>The deadline is {@link #DEADLINE}, measured with the injected clock at the tick. A read still waiting when the deadline
 * passes is answered "unavailable" on that tick, and a result that arrives later is dropped. A request the queue cannot
 * take, or a read the pool refuses, is answered unavailable too, so a player is never left with silence.
 *
 * <p>The request side is any thread. Everything else, including the pending list, belongs to the tick thread.
 */
public final class StatsRequests {

    /** The name prefix of the reader threads, as it appears in dumps and logs. */
    public static final String READER_THREAD_PREFIX = "voyager-db-read";

    /** The number of reader threads. */
    public static final int READER_THREADS = 2;

    /** How long a read may take, from the request, before it is answered unavailable. */
    public static final Duration DEADLINE = Duration.ofSeconds(2);

    /** The most chat lines one tick sends for reads, so a burst of commands cannot flood a tick. */
    public static final int REPLIES_PER_TICK = 8;

    /** Reads that may wait for a free reader thread. */
    static final int READ_QUEUE_CAPACITY = 32;

    /** Requests accepted but not yet taken in by the tick. */
    static final int INCOMING_CAPACITY = 32;

    /** Results waiting for the tick to send them. */
    static final int FINISHED_CAPACITY = 64;

    /** Refusals waiting for the tick to answer them "unavailable". */
    static final int REFUSED_CAPACITY = 32;

    private final Executor readers;
    private final Clock clock;
    private final BlockingQueue<Request<?>> incoming = new ArrayBlockingQueue<>(INCOMING_CAPACITY);
    private final BlockingQueue<Finished<?>> finished = new ArrayBlockingQueue<>(FINISHED_CAPACITY);
    private final BlockingQueue<Consumer<Component>> refused = new ArrayBlockingQueue<>(REFUSED_CAPACITY);
    private final AtomicLong droppedReplies = new AtomicLong();
    /** Tick thread only: the requests taken in and not yet answered, oldest first. */
    private final List<Request<?>> pending = new ArrayList<>();

    /**
     * @param readers the executor the reads run on; the server passes {@link #readerPool()}
     * @param clock   the source of the deadline; the server passes its clock, the tests a stepped one
     */
    public StatsRequests(Executor readers, Clock clock) {
        this.readers = readers;
        this.clock = clock;
    }

    /**
     * The pool the server reads on: two daemon threads named {@value #READER_THREAD_PREFIX}, with a bounded queue of reads.
     * It is deliberately not {@code ForkJoinPool.commonPool()}, so a slow database cannot starve other work that uses it.
     *
     * @return a new pool; the server keeps the one it builds
     */
    public static ThreadPoolExecutor readerPool() {
        AtomicInteger started = new AtomicInteger();
        return new ThreadPoolExecutor(READER_THREADS, READER_THREADS, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(READ_QUEUE_CAPACITY), task -> {
                    Thread thread = new Thread(task, READER_THREAD_PREFIX + "-" + started.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
    }

    /**
     * Asks for one read. Called from any thread; it never waits for the read and never sends anything itself. When the
     * request queue is full the request is refused, and the tick answers "unavailable" for it. If the refusal queue is full
     * too, that answer is dropped and counted in {@link #droppedReplies()}, rather than blocking the caller.
     *
     * @param replyTo where each line of the answer goes; the tick calls it
     * @param read    the read; runs on the reader pool and may throw
     * @param answer  turns the read's value into the lines to send; runs on the tick
     * @param <T>     the value the read returns
     */
    public <T> void ask(Consumer<Component> replyTo, Supplier<T> read, Function<T, List<Component>> answer) {
        Request<T> request = new Request<>(replyTo, read, answer, clock.instant().plus(DEADLINE));
        if (!incoming.offer(request)) {
            if (!refused.offer(replyTo)) {
                droppedReplies.incrementAndGet();
            }
            return;
        }
        try {
            readers.execute(() -> finished.offer(Finished.read(request)));
        } catch (RejectedExecutionException refused) {
            finished.offer(Finished.failed(request));
        }
    }

    /**
     * One pass of the tick: takes in the new requests, answers the expired ones, then sends the finished reads, within the
     * per-tick budget. Called once per tick, on the tick thread.
     */
    public void tick() {
        Instant now = clock.instant();
        Request<?> arrived;
        while ((arrived = incoming.poll()) != null) {
            pending.add(arrived);
        }
        int replies = 0;
        Iterator<Request<?>> waiting = pending.iterator();
        while (waiting.hasNext()) {
            Request<?> request = waiting.next();
            if (request.answered) {
                waiting.remove();
            } else if (replies < REPLIES_PER_TICK && !now.isBefore(request.deadline)) {
                waiting.remove();
                request.answered = true;
                send(request.replyTo, List.of(Messages.statsUnavailable()));
                replies++;
            }
        }
        Consumer<Component> target;
        while (replies < REPLIES_PER_TICK && (target = refused.poll()) != null) {
            send(target, List.of(Messages.statsUnavailable()));
            replies++;
        }
        Finished<?> result;
        while (replies < REPLIES_PER_TICK && (result = finished.poll()) != null) {
            if (result.request().answered) {
                continue;
            }
            reply(result);
            replies++;
        }
    }

    /**
     * How many answers were dropped because even the refusal queue was full. Read from any thread.
     *
     * @return the count since this instance was built
     */
    public long droppedReplies() {
        return droppedReplies.get();
    }

    private static <T> void reply(Finished<T> result) {
        Request<T> request = result.request();
        request.answered = true;
        List<Component> lines = result.failed()
                ? List.of(Messages.statsUnavailable())
                : request.answer.apply(result.value());
        send(request.replyTo, lines);
    }

    private static void send(Consumer<Component> replyTo, List<Component> lines) {
        lines.forEach(replyTo);
    }

    /** One question: where the answer goes, how to read it, how to word it and when it is out of time. */
    private static final class Request<T> {

        private final Consumer<Component> replyTo;
        private final Supplier<T> read;
        private final Function<T, List<Component>> answer;
        private final Instant deadline;
        /** Tick thread only. Once true, the request has been answered, and any later result for it is dropped. */
        private boolean answered;

        Request(Consumer<Component> replyTo, Supplier<T> read, Function<T, List<Component>> answer, Instant deadline) {
            this.replyTo = replyTo;
            this.read = read;
            this.answer = answer;
            this.deadline = deadline;
        }
    }

    /** One finished read, waiting for the tick. {@code value} is set only when {@code failed} is false. */
    private record Finished<T>(Request<T> request, @Nullable T value, boolean failed) {

        static <T> Finished<T> read(Request<T> request) {
            try {
                return new Finished<>(request, request.read.get(), false);
            } catch (RuntimeException unavailable) {
                return failed(request);
            }
        }

        static <T> Finished<T> failed(Request<T> request) {
            return new Finished<>(request, null, true);
        }
    }
}
