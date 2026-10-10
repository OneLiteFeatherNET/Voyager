package net.elytrarace.voyager.server.command;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The server console reader, driven by an in-memory stream and a fake dispatcher. No server, no socket, no thread
 * wait: {@link ConsoleCommandReader#run()} returns when its input ends.
 */
class ConsoleCommandReaderTest {

    /** Records every line it is asked to run and knows only the commands in {@code known}. */
    private static final class FakeDispatcher implements ConsoleCommandReader.Dispatcher {

        private final Set<String> known;
        private final List<String> dispatched = new ArrayList<>();

        FakeDispatcher(String... known) {
            this.known = Set.of(known);
        }

        @Override
        public boolean dispatch(String line) {
            dispatched.add(line);
            return known.contains(line);
        }
    }

    private static InputStream input(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void dispatchesEachLineInTheOrderItWasTyped() {
        FakeDispatcher dispatcher = new FakeDispatcher("race reload", "race status");
        List<String> replies = new ArrayList<>();

        new ConsoleCommandReader(input("race reload\nrace status\n"), dispatcher, replies::add).run();

        assertThat(dispatcher.dispatched).containsExactly("race reload", "race status");
    }

    @Test
    void trimsSurroundingWhitespaceBeforeDispatching() {
        FakeDispatcher dispatcher = new FakeDispatcher("race reload");

        new ConsoleCommandReader(input("   race reload  \n"), dispatcher, line -> { }).run();

        assertThat(dispatcher.dispatched).containsExactly("race reload");
    }

    @Test
    void ignoresBlankLines() {
        FakeDispatcher dispatcher = new FakeDispatcher("race reload");

        new ConsoleCommandReader(input("\n   \nrace reload\n\n"), dispatcher, line -> { }).run();

        assertThat(dispatcher.dispatched).containsExactly("race reload");
    }

    @Test
    void repliesWithOneLineWhenNoCommandMatches() {
        FakeDispatcher dispatcher = new FakeDispatcher("race reload");
        List<String> replies = new ArrayList<>();

        new ConsoleCommandReader(input("race bogus\n"), dispatcher, replies::add).run();

        assertThat(replies).containsExactly("Unknown command: race bogus");
    }

    @Test
    void doesNotReplyWhenACommandRan() {
        FakeDispatcher dispatcher = new FakeDispatcher("race reload");
        List<String> replies = new ArrayList<>();

        new ConsoleCommandReader(input("race reload\n"), dispatcher, replies::add).run();

        assertThat(replies).isEmpty();
    }

    @Test
    void endsWithoutDispatchingWhenTheInputIsAlreadyClosed() {
        FakeDispatcher dispatcher = new FakeDispatcher("race reload");
        List<String> replies = new ArrayList<>();

        new ConsoleCommandReader(input(""), dispatcher, replies::add).run();

        assertThat(dispatcher.dispatched).isEmpty();
        assertThat(replies).isEmpty();
    }

    @Test
    void endsQuietlyWhenReadingTheInputFails() {
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("stdin is gone");
            }
        };
        FakeDispatcher dispatcher = new FakeDispatcher("race reload");

        new ConsoleCommandReader(broken, dispatcher, line -> { }).run();

        assertThat(dispatcher.dispatched).isEmpty();
    }

    @Test
    void dispatchesNothingAfterStop() {
        FakeDispatcher dispatcher = new FakeDispatcher("race reload");
        ConsoleCommandReader reader = new ConsoleCommandReader(input("race reload\n"), dispatcher, line -> { });

        reader.stop();
        reader.run();

        assertThat(dispatcher.dispatched).isEmpty();
    }

    @Test
    void keepsReadingAfterACommandThrows() {
        List<String> dispatched = new ArrayList<>();
        ConsoleCommandReader.Dispatcher failsOnFirst = line -> {
            dispatched.add(line);
            if (line.equals("race reload")) {
                throw new IllegalStateException("the parser broke");
            }
            return true;
        };
        List<String> replies = new ArrayList<>();

        new ConsoleCommandReader(input("race reload\nrace status\n"), failsOnFirst, replies::add).run();

        assertThat(dispatched).containsExactly("race reload", "race status");
        assertThat(replies).containsExactly("Command failed: race reload");
    }

    @Test
    void runsOnADaemonVirtualThread() throws InterruptedException {
        ConsoleCommandReader reader = new ConsoleCommandReader(input(""), new FakeDispatcher(), line -> { });

        Thread thread = reader.start();
        thread.join();

        assertThat(thread.isVirtual()).as("the reader must not hold a platform thread").isTrue();
        assertThat(thread.isDaemon()).as("the reader must not keep the JVM alive").isTrue();
    }
}
