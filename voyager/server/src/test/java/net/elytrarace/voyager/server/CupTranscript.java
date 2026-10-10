package net.elytrarace.voyager.server;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.ActionBarPacket;
import net.minestom.server.network.packet.server.play.SetTitleSubTitlePacket;
import net.minestom.server.network.packet.server.play.SetTitleTextPacket;
import net.minestom.server.network.packet.server.play.SetTitleTimePacket;
import net.minestom.server.network.packet.server.play.SystemChatPacket;
import net.minestom.testing.Collector;
import net.minestom.testing.TestConnection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Writes the observable behaviour of one cup, step by step, as the text a Golden Master compares.
 *
 * <p>A step is one action and, when it ticks, one server tick. For each step the transcript records the
 * step's label, the full {@code describe()} text after it, every chat, action-bar and title packet each
 * seated connection received during it, in order, and every log line the Voyager loggers wrote during it.
 * A log line is its level and its formatted message; the logger name is deliberately left out, because a
 * move between packages changes it.
 *
 * <p>Messages are rendered as the Adventure component's JSON, so the message key and its arguments are
 * pinned, not just the English text the bundle happens to give them.
 *
 * <p>Minestom's {@code Collector} is one-shot: {@code collect()} detaches it. A step therefore opens a fresh
 * collector per connection before it acts and collects it after the tick, which is also what keeps the
 * packets of one step out of the next.
 */
final class CupTranscript implements AutoCloseable {

    private static final String TAP = "cup-transcript";
    private static final String VOYAGER_LOGGER = "net.elytrarace.voyager";

    private final Map<String, TestConnection> connections = new LinkedHashMap<>();
    private final Map<String, Collector<ServerPacket>> open = new LinkedHashMap<>();
    private final List<String> lines = new ArrayList<>();
    private final List<String> logLines = new CopyOnWriteArrayList<>();
    private final LoggerContext context;
    private final LoggerConfig logger;
    private final Appender tap;

    CupTranscript() {
        this.context = (LoggerContext) LogManager.getContext(false);
        this.tap = new AbstractAppender(TAP, null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                logLines.add("%s %s".formatted(event.getLevel(), event.getMessage().getFormattedMessage()));
            }
        };
        this.tap.start();
        this.logger = context.getConfiguration().getLoggerConfig(VOYAGER_LOGGER);
        this.logger.addAppender(tap, null, null);
        context.updateLoggers();
    }

    /** Names a connection whose packets the transcript records. Seats keep the order they were given. */
    void seat(String name, TestConnection connection) {
        connections.put(name, connection);
    }

    /** Opens the step: a fresh packet collector per seat, and an empty log buffer. */
    void begin() {
        open.clear();
        logLines.clear();
        for (Map.Entry<String, TestConnection> seat : connections.entrySet()) {
            open.put(seat.getKey(), seat.getValue().trackIncoming());
        }
    }

    /** Closes the step, recording its label, the cup's state and everything it sent and logged. */
    void end(String label, String describe) {
        lines.add("## " + label);
        lines.add("describe");
        for (String line : describe.split("\n")) {
            lines.add("  " + line);
        }
        lines.add("messages");
        for (Map.Entry<String, Collector<ServerPacket>> tracker : open.entrySet()) {
            for (ServerPacket packet : tracker.getValue().collect()) {
                String rendered = render(packet);
                if (rendered != null) {
                    lines.add("  %s %s".formatted(tracker.getKey(), rendered));
                }
            }
        }
        open.clear();
        lines.add("logs");
        for (String logged : logLines) {
            lines.add("  " + logged);
        }
    }

    /** The whole transcript, one step after another, with a final line break. */
    String text() {
        return String.join("\n", lines) + "\n";
    }

    private static String render(ServerPacket packet) {
        if (packet instanceof SystemChatPacket chat) {
            return "chat%s %s".formatted(chat.overlay() ? " overlay" : "", json(chat.message()));
        }
        if (packet instanceof SetTitleTextPacket title) {
            return "title " + json(title.title());
        }
        if (packet instanceof SetTitleSubTitlePacket subtitle) {
            return "subtitle " + json(subtitle.subtitle());
        }
        if (packet instanceof SetTitleTimePacket times) {
            return "title-times %s %s %s".formatted(times.fadeIn(), times.stay(), times.fadeOut());
        }
        if (packet instanceof ActionBarPacket bar) {
            return "actionbar " + json(bar.components().getFirst());
        }
        return null;
    }

    private static String json(Component component) {
        return GsonComponentSerializer.gson().serialize(component);
    }

    /** Detaches the log tap. Every test closes its transcript, so no appender outlives its test. */
    @Override
    public void close() {
        logger.removeAppender(TAP);
        tap.stop();
        context.updateLoggers();
    }
}
