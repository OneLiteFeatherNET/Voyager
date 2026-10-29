package net.elytrarace.voyager.setup.adapter;

import net.elytrarace.voyager.api.mapsetup.DraftStore;
import net.elytrarace.voyager.api.mapsetup.ImportReport;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.mapsetup.StatedSpawn;
import net.elytrarace.voyager.api.mapsetup.WorldFolderName;
import net.elytrarace.voyager.api.mapsetup.WorldImporter;
import net.elytrarace.voyager.api.mapsetup.exception.DraftLocationConflictException;
import net.elytrarace.voyager.api.mapsetup.exception.DraftNotFoundException;
import net.elytrarace.voyager.api.mapsetup.exception.InvalidDraftException;
import net.elytrarace.voyager.api.mapsetup.exception.InvalidMapIdException;
import net.elytrarace.voyager.api.mapsetup.exception.InvalidWorldFolderNameException;
import net.elytrarace.voyager.api.mapsetup.exception.WorldImportRefusedException;
import net.elytrarace.voyager.api.permission.PermissionNode;
import net.elytrarace.voyager.api.permission.PermissionPolicy;
import net.elytrarace.voyager.api.permission.PermissionSubject;
import net.elytrarace.voyager.platform.text.Messages;
import net.elytrarace.voyager.platform.text.SetupMessages;
import net.elytrarace.voyager.setup.mapsetup.DraftEditor;
import net.elytrarace.voyager.setup.mapsetup.ImportGuard;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * {@code /map import <folder> <id>}: copies a finished world into a new map and saves its draft, without a player.
 *
 * <p>The sender is checked for both {@code voyager.setup.use} and {@code voyager.setup.import}. The id and the folder
 * name are checked before any file is read. The copy, the survey and the draft save run on {@code background}; the
 * reply is delivered on {@code mainThread}, so a long copy never stalls the tick. The id is held by the guard from the
 * claim until the background work ends, and released before the reply.
 *
 * <p>The class names no Minestom type: the command syntax is attached by the composition root
 * ({@code setup.inject.ImportSyntax}), so this adapter is outside the Minestom boundary rule.
 */
public final class ImportCommand {

    private final DraftStore store;
    private final WorldImporter importer;
    private final ImportGuard guard;
    private final PermissionPolicy policy;
    private final Executor background;
    private final Executor mainThread;

    /**
     * @param store      the draft store the draft is saved through
     * @param importer   the importer that copies and surveys the world
     * @param guard      the in-memory claims of the ids being imported
     * @param policy     answers the two nodes the import needs
     * @param background runs the copy, the survey and the save
     * @param mainThread delivers the reply
     */
    public ImportCommand(DraftStore store, WorldImporter importer, ImportGuard guard, PermissionPolicy policy,
            Executor background, Executor mainThread) {
        this.store = store;
        this.importer = importer;
        this.guard = guard;
        this.policy = policy;
        this.background = background;
        this.mainThread = mainThread;
    }

    /**
     * Runs the command for one sender. The reply is sent through {@code reply}, which the caller binds to the sender.
     *
     * @param sender the sender, checked against the two nodes
     * @param folder the world folder name under the import root, as typed
     * @param rawId  the map id, as typed
     * @param reply  receives every line of the reply, in order
     */
    public void run(PermissionSubject sender, String folder, String rawId, Consumer<Component> reply) {
        if (!policy.allows(sender, PermissionNode.VOYAGER_SETUP_USE)
                || !policy.allows(sender, PermissionNode.VOYAGER_SETUP_IMPORT)) {
            reply.accept(Messages.commandDenied());
            return;
        }
        MapId id;
        try {
            id = new MapId(rawId);
        } catch (InvalidMapIdException exception) {
            reply.accept(SetupMessages.invalidId(rawId));
            return;
        }
        WorldFolderName name;
        try {
            name = new WorldFolderName(folder);
        } catch (InvalidWorldFolderNameException exception) {
            reply.accept(SetupMessages.importFolderInvalid(folder));
            return;
        }
        if (guard.isClaimed(id)) {
            reply.accept(SetupMessages.importBusy(id.value()));
            return;
        }
        if (draftExists(id)) {
            reply.accept(SetupMessages.alreadyExists(id.value()));
            return;
        }
        if (!guard.claim(id)) {
            reply.accept(SetupMessages.importBusy(id.value()));
            return;
        }
        reply.accept(SetupMessages.importStarted(folder, id.value()));
        background.execute(() -> {
            List<Component> lines = importAndSave(name, id);
            mainThread.execute(() -> lines.forEach(reply));
        });
    }

    private boolean draftExists(MapId id) {
        try {
            store.load(id);
            return true;
        } catch (DraftNotFoundException absent) {
            return false;
        } catch (DraftLocationConflictException | InvalidDraftException conflict) {
            return true;
        }
    }

    /** Runs on the background thread and returns the lines of the reply. The id is released before it returns. */
    private List<Component> importAndSave(WorldFolderName folder, MapId id) {
        try {
            ImportReport report = importer.importWorld(folder, id);
            MapDraft skeleton = DraftEditor.skeleton(id);
            MapDraft draft = report.spawn() == null ? skeleton
                    : DraftEditor.withSpawn(skeleton, report.spawn().centre());
            try {
                store.save(draft);
            } catch (RuntimeException failure) {
                discardAfterFailedSave(id, failure);
                throw failure;
            }
            return done(id, report);
        } catch (WorldImportRefusedException refusal) {
            return List.of(refusalLine(id, refusal));
        } catch (RuntimeException failure) {
            return List.of(SetupMessages.importFailed(id.value(), String.valueOf(failure.getMessage())));
        } finally {
            guard.release(id);
        }
    }

    private void discardAfterFailedSave(MapId id, RuntimeException failure) {
        try {
            importer.discard(id);
        } catch (RuntimeException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }

    private static List<Component> done(MapId id, ImportReport report) {
        List<Component> lines = new ArrayList<>();
        lines.add(SetupMessages.importDone(id.value(), report.regionFiles(), report.regionBytes()));
        lines.add(SetupMessages.importCensus(report.census().full(), report.census().partial(),
                report.census().withoutStatus(), report.census().unreadable()));
        if (report.census().partial() > 0) {
            lines.add(SetupMessages.importPartialWarning(report.census().partial(), report.census().partialPercent()));
        }
        StatedSpawn spawn = report.spawn();
        if (spawn == null) {
            lines.add(SetupMessages.importSpawnNone());
        } else {
            String source = spawn.source() == StatedSpawn.Source.MODERN ? "Data.spawn" : "SpawnX, SpawnY, SpawnZ";
            lines.add(SetupMessages.importSpawnFound(spawn.x(), spawn.y(), spawn.z(), source));
            if (spawn.looksDefault()) {
                lines.add(SetupMessages.importSpawnHint(spawn.x(), spawn.y(), spawn.z()));
            }
        }
        if (!report.ignoredDimensions().isEmpty()) {
            lines.add(SetupMessages.importDimensionsIgnored(String.join(", ", report.ignoredDimensions())));
        }
        return List.copyOf(lines);
    }

    /** Maps a refusal to its message: the reason picks the key, and the arguments fill its placeholders. */
    private static Component refusalLine(MapId id, WorldImportRefusedException refusal) {
        List<String> arguments = refusal.arguments();
        return switch (refusal.reason()) {
            case ROOT_MISSING -> SetupMessages.importRootMissing(arguments.get(0));
            case FOLDER_INVALID -> SetupMessages.importFolderInvalid(arguments.get(0));
            case FOLDER_MISSING -> SetupMessages.importFolderMissing(arguments.get(0));
            case LAYOUT_UNSUPPORTED -> SetupMessages.importLayoutUnsupported();
            case LAYOUT_AMBIGUOUS -> SetupMessages.importLayoutAmbiguous(arguments.get(0), arguments.get(1));
            case LINK -> SetupMessages.importLinkRefused(arguments.get(0));
            case TOO_LARGE -> SetupMessages.importTooLarge(arguments.get(0), arguments.get(1), arguments.get(2));
            case DATA_VERSION_NEWER -> SetupMessages.importDataVersionNewer(arguments.get(0), arguments.get(1));
            case WORLD_EXISTS -> SetupMessages.alreadyExists(id.value());
        };
    }
}
