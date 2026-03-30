package net.elytrarace.setup.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Persists {@link SetupSession} snapshots as JSON files so that sessions survive
 * player disconnects and server restarts.
 * <p>
 * Each session is stored in {@code plugins/ElytraRace-Setup/sessions/<uuid>.json}.
 */
public final class JsonSessionPersistence {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonSessionPersistence.class);

    private final Path sessionsDir;
    private final Gson gson;

    public JsonSessionPersistence(Path pluginDataDir) {
        this.sessionsDir = pluginDataDir.resolve("sessions");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            Files.createDirectories(sessionsDir);
        } catch (IOException e) {
            LOGGER.warn("Unable to create sessions directory: {}", sessionsDir, e);
        }
    }

    /**
     * Asynchronously saves the given session to disk.
     *
     * @param session the session to persist
     * @return a future that completes when the write is done
     */
    public CompletableFuture<Void> save(SetupSession session) {
        Objects.requireNonNull(session, "session must not be null");
        return CompletableFuture.runAsync(() -> {
            var snapshot = SessionSnapshot.from(session);
            var filePath = sessionFile(session.playerId());
            try {
                Files.createDirectories(sessionsDir);
                var json = gson.toJson(snapshot);
                Files.writeString(filePath, json, StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOGGER.warn("Failed to save session for player {}: {}", session.playerId(), e.getMessage());
            }
        });
    }

    /**
     * Loads a persisted session snapshot for the given player.
     *
     * @param playerId the player UUID
     * @return the restored session, or empty if no persisted data exists
     */
    public Optional<SetupSession> load(UUID playerId) {
        var filePath = sessionFile(playerId);
        if (!Files.exists(filePath)) {
            return Optional.empty();
        }
        try {
            var json = Files.readString(filePath, StandardCharsets.UTF_8);
            var snapshot = gson.fromJson(json, SessionSnapshot.class);
            if (snapshot == null) {
                return Optional.empty();
            }
            // Delete the file after loading — session is now in memory
            Files.deleteIfExists(filePath);
            return Optional.of(snapshot.toSession());
        } catch (IOException e) {
            LOGGER.warn("Failed to load session for player {}: {}", playerId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Saves all currently active sessions. Call from {@code onDisable()}.
     *
     * @param manager the session manager holding active sessions
     */
    public void saveAll(SetupSessionManager manager) {
        for (var session : manager.all()) {
            try {
                var snapshot = SessionSnapshot.from(session);
                var filePath = sessionFile(session.playerId());
                Files.createDirectories(sessionsDir);
                var json = gson.toJson(snapshot);
                Files.writeString(filePath, json, StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOGGER.warn("Failed to save session for player {} during shutdown: {}",
                        session.playerId(), e.getMessage());
            }
        }
    }

    /**
     * Deletes session files that are older than the given maximum age.
     * Call from {@code onDisable()} to clean up stale sessions.
     *
     * @param maxAge the maximum age before a session file is considered expired
     */
    public void deleteExpired(Duration maxAge) {
        if (!Files.exists(sessionsDir)) {
            return;
        }
        var cutoff = Instant.now().minus(maxAge);
        try (Stream<Path> files = Files.list(sessionsDir)) {
            files.filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            var lastModified = Files.getLastModifiedTime(path).toInstant();
                            if (lastModified.isBefore(cutoff)) {
                                Files.deleteIfExists(path);
                                LOGGER.info("Deleted expired session file: {}", path.getFileName());
                            }
                        } catch (IOException e) {
                            LOGGER.warn("Failed to check/delete session file {}: {}",
                                    path.getFileName(), e.getMessage());
                        }
                    });
        } catch (IOException e) {
            LOGGER.warn("Failed to list session files for cleanup: {}", e.getMessage());
        }
    }

    private Path sessionFile(UUID playerId) {
        return sessionsDir.resolve(playerId.toString() + ".json");
    }
}
