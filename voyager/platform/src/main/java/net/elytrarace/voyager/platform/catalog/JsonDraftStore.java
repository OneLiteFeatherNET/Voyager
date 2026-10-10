package net.elytrarace.voyager.platform.catalog;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.elytrarace.voyager.api.mapsetup.DraftStore;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.mapsetup.exception.DraftAlreadyExistsException;
import net.elytrarace.voyager.api.mapsetup.exception.DraftLocationConflictException;
import net.elytrarace.voyager.api.mapsetup.exception.DraftNotFoundException;
import net.elytrarace.voyager.api.mapsetup.exception.DraftWriteFailedException;
import net.elytrarace.voyager.api.mapsetup.exception.InvalidDraftException;
import net.elytrarace.voyager.platform.catalog.adapter.MapDraftAdapter;
import net.elytrarace.voyager.platform.catalog.writer.MapDraftJsonWriter;
import net.elytrarace.voyager.platform.world.VoidWorldTemplate;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * The file-system implementation of {@link DraftStore}, over the game's own layout.
 *
 * <p>A draft that has a spawn and at least one ring is game-loadable, and it is written to {@code maps/<id>.json}, the
 * one file the game server reads. Every other draft is written to {@code drafts/<id>.json}, a folder the game never
 * reads. The world of a map is {@code <worldsPath>/<id>} in both cases.
 *
 * <p>A save writes a temporary file in the target folder, forces it to disk, and moves it over the target as an
 * atomic replace. Only after that does it delete the copy in the other folder, so there is no moment with no copy of
 * the draft, and a crash between the two steps leaves two copies. {@link #load} refuses two copies and names both,
 * because either one could hold the last action.
 *
 * <p>{@link FileMover} is the seam that lets a test make the move fail, which a real file system does not do on
 * demand.
 */
@ApiStatus.Internal
public final class JsonDraftStore implements DraftStore {

    private static final String MAPS = "maps";
    private static final String DRAFTS = "drafts";
    private static final String JSON = ".json";
    private static final String TEMPORARY = ".tmp";

    private final Path maps;
    private final Path drafts;
    private final Path worlds;
    private final FileMover mover;

    /**
     * @param dataPath  the data directory, holding {@code maps/} and {@code drafts/}
     * @param worldsPath the directory holding one world folder per map
     */
    public JsonDraftStore(Path dataPath, Path worldsPath) {
        this(dataPath, worldsPath, Files::move);
    }

    /**
     * @param dataPath   the data directory, holding {@code maps/} and {@code drafts/}
     * @param worldsPath the directory holding one world folder per map
     * @param mover      the move used for the atomic replace; {@code Files::move} in production
     */
    public JsonDraftStore(Path dataPath, Path worldsPath, FileMover mover) {
        this.maps = dataPath.resolve(MAPS);
        this.drafts = dataPath.resolve(DRAFTS);
        this.worlds = worldsPath;
        this.mover = mover;
    }

    /** Moves a file, as {@code Files.move} does; a test substitutes one that fails. */
    @FunctionalInterface
    public interface FileMover {
        void move(Path source, Path target, CopyOption... options) throws IOException;
    }

    /**
     * Copies the void world first and writes the skeleton second. A failed copy leaves no draft; a failed draft write
     * takes the copied world back, so either both exist afterwards or neither does.
     */
    @Override
    public MapDraft create(MapDraft skeleton) {
        MapId id = skeleton.id();
        Path world = worlds.resolve(id.value());
        if (Files.exists(mapFile(id)) || Files.exists(draftFile(id)) || Files.isDirectory(world)) {
            throw DraftAlreadyExistsException.of(id);
        }
        VoidWorldTemplate.copyTo(world);
        try {
            save(skeleton);
        } catch (RuntimeException exception) {
            try {
                VoidWorldTemplate.remove(world);
            } catch (UncheckedIOException cleanup) {
                exception.addSuppressed(cleanup);
            }
            throw exception;
        }
        return skeleton;
    }

    @Override
    public MapDraft load(MapId id) {
        Path mapFile = mapFile(id);
        Path draftFile = draftFile(id);
        boolean inMaps = Files.isRegularFile(mapFile);
        boolean inDrafts = Files.isRegularFile(draftFile);
        if (inMaps && inDrafts) {
            throw DraftLocationConflictException.of(id, mapFile.toString(), draftFile.toString());
        }
        if (!inMaps && !inDrafts) {
            throw DraftNotFoundException.of(id);
        }
        return read(id, inMaps ? mapFile : draftFile);
    }

    @Override
    public void save(MapDraft draft) {
        MapId id = draft.id();
        boolean gameLoadable = draft.spawn() != null && !draft.rings().isEmpty();
        Path target = gameLoadable ? mapFile(id) : draftFile(id);
        Path stale = gameLoadable ? draftFile(id) : mapFile(id);

        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), id.value() + "-", TEMPORARY);
            Files.writeString(temporary, MapDraftJsonWriter.toJson(draft), StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            replace(temporary, target);
        } catch (IOException | RuntimeException exception) {
            deleteQuietly(temporary);
            throw DraftWriteFailedException.of(id, exception);
        }

        // The new copy exists now; the old one is stale, and deleting it is safe.
        try {
            Files.deleteIfExists(stale);
        } catch (IOException exception) {
            throw DraftWriteFailedException.of(id, exception);
        }
    }

    private void replace(Path temporary, Path target) throws IOException {
        try {
            mover.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            mover.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static MapDraft read(MapId id, Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw InvalidDraftException.unreadable(id, "%s could not be read: %s".formatted(file, exception.getMessage()));
        }
        MapDraft draft;
        try {
            draft = MapDraftAdapter.read(file.getFileName().toString(), JsonParser.parseString(text));
        } catch (JsonParseException exception) {
            throw InvalidDraftException.unreadable(id, exception.getMessage());
        }
        if (!draft.id().equals(id)) {
            throw InvalidDraftException.unreadable(id,
                    "%s names the map '%s'".formatted(file.getFileName(), draft.id().value()));
        }
        return draft;
    }

    private static void deleteQuietly(@Nullable Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // The save has already failed and is reported; a temporary file left behind is named by its prefix.
        }
    }

    private Path mapFile(MapId id) {
        return maps.resolve(id.value() + JSON);
    }

    private Path draftFile(MapId id) {
        return drafts.resolve(id.value() + JSON);
    }
}
