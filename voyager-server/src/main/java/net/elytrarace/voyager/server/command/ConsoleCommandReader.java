package net.elytrarace.voyager.server.command;

import net.minestom.server.command.CommandManager;
import net.minestom.server.command.builder.CommandResult;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Reads commands typed into the server console and runs each one as the console sender.
 *
 * <p>Minestom 26.2 does not read standard input on its own, so without this an operator has no way to run
 * {@code race reload} on a running server. The reader is a daemon virtual thread: a blocked read cannot hold the
 * JVM open, and the server's own shutdown does not wait for it.
 *
 * <p>The rules:
 * <ul>
 *   <li>Each non-blank line, trimmed, is handed to the {@link Dispatcher}, in the order it was typed.</li>
 *   <li>A line no command matches gets one reply line, {@code Unknown command: <line>}.</li>
 *   <li>A line whose dispatch throws is logged with its cause and gets one reply line; the reader keeps going.</li>
 *   <li>End of input, a closed or unreadable stream, or a stopped reader ends the loop quietly. A non-interactive
 *       server (stdin from {@code /dev/null}, or no stdin) hits end of input at once and runs with no console.</li>
 * </ul>
 *
 * <p>The input is injected, so the tests read an in-memory stream and never touch {@code System.in}. The stream is
 * never closed, because it is the JVM's standard input.
 */
public final class ConsoleCommandReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleCommandReader.class);

    /** Runs one typed line as a command. */
    @FunctionalInterface
    public interface Dispatcher {

        /**
         * @param line the trimmed, non-blank line
         * @return {@code true} when a command matched and ran, {@code false} when none matched
         */
        boolean dispatch(String line);
    }

    private final BufferedReader lines;
    private final Dispatcher dispatcher;
    private final Consumer<String> reply;
    private final AtomicBoolean stopped = new AtomicBoolean();

    /**
     * @param input      where the lines come from; {@code System.in} in production
     * @param dispatcher runs each line
     * @param reply      receives the one-line replies the reader itself writes
     */
    public ConsoleCommandReader(InputStream input, Dispatcher dispatcher, Consumer<String> reply) {
        this.lines = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        this.dispatcher = dispatcher;
        this.reply = reply;
    }

    /**
     * Starts the reader on a new daemon virtual thread.
     *
     * @return the thread, so a caller or a test can wait for it to end
     */
    public Thread start() {
        return Thread.ofVirtual().name("voyager-console").start(this::run);
    }

    /**
     * Reads and dispatches lines until the input ends or {@link #stop()} is called. Returns; never throws for a
     * failed read.
     */
    public void run() {
        while (!stopped.get()) {
            String line = nextLine();
            if (line == null) {
                return;
            }
            String command = line.trim();
            if (!command.isEmpty()) {
                dispatch(command);
            }
        }
    }

    /**
     * Stops the loop before its next line. A read already blocked on the stream stays blocked until the stream
     * yields a line or the JVM exits, which is harmless because the thread is a daemon.
     */
    public void stop() {
        stopped.set(true);
    }

    private @Nullable String nextLine() {
        try {
            return lines.readLine();
        } catch (IOException failure) {
            LOGGER.debug("Console input ended with a read failure; no more commands will be read", failure);
            return null;
        }
    }

    private void dispatch(String command) {
        try {
            if (!dispatcher.dispatch(command)) {
                reply.accept("Unknown command: %s".formatted(command));
            }
        } catch (RuntimeException failure) {
            LOGGER.error("Console command failed: {}", command, failure);
            reply.accept("Command failed: %s".formatted(command));
        }
    }

    /**
     * The production dispatcher: runs the line through Minestom's command manager as the console sender.
     *
     * @param commands the server's command manager
     * @return a dispatcher whose unknown answer is {@code false}
     */
    public static Dispatcher consoleOf(CommandManager commands) {
        return line -> commands.execute(commands.getConsoleSender(), line).getType() != CommandResult.Type.UNKNOWN;
    }
}
