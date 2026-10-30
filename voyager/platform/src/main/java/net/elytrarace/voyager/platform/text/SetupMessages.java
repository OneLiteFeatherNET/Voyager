package net.elytrarace.voyager.platform.text;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Contract;

import java.util.List;

/**
 * Every sentence the setup server says to a builder, as a translation key with its arguments bound. The keys live in
 * {@code voyager_setup_en_US.properties}, shipped by voyager-setup and loaded with
 * {@link VoyagerTranslator#fromClasspath(String)}, which reads every locale of the bundle.
 */
public abstract class SetupMessages {

    /** The base name of the bundle the setup server loads. */
    public static final String BUNDLE = "voyager_setup";

    private static final String ID_INVALID = "voyager.setup.id.invalid";
    private static final String MAP_CREATED = "voyager.setup.map.created";
    private static final String MAP_EXISTS = "voyager.setup.map.exists";
    private static final String MAP_OPENED = "voyager.setup.map.opened";
    private static final String MAP_NONE = "voyager.setup.map.none";
    private static final String REFUSED = "voyager.setup.refused";
    private static final String PLAYER_ONLY = "voyager.setup.player.only";
    private static final String WAND_GIVEN_PLACE = "voyager.setup.wand.given.place";
    private static final String WAND_GIVEN_REMOVE = "voyager.setup.wand.given.remove";
    private static final String SPAWN_SAVED = "voyager.setup.spawn.saved";
    private static final String SPAWN_ELSEWHERE = "voyager.setup.spawn.elsewhere";
    private static final String SAVE_FAILED = "voyager.setup.save.failed";
    private static final String STATUS_SPAWN_SET = "voyager.setup.status.spawn.set";
    private static final String STATUS_SPAWN_UNSET = "voyager.setup.status.spawn.unset";
    private static final String STATUS_RINGS = "voyager.setup.status.rings";
    private static final String STATUS_PROBLEM_SPAWN = "voyager.setup.status.problem.spawn";
    private static final String STATUS_PROBLEM_RINGS = "voyager.setup.status.problem.rings";
    private static final String RING_PLACED = "voyager.setup.ring.placed";
    private static final String RING_POSE_REFUSED = "voyager.setup.ring.pose.refused";
    private static final String RING_REMOVED = "voyager.setup.ring.removed";
    private static final String RING_NONE = "voyager.setup.ring.none";
    private static final String UNDO_DONE = "voyager.setup.undo.done";
    private static final String UNDO_NONE = "voyager.setup.undo.none";
    private static final String REDO_DONE = "voyager.setup.redo.done";
    private static final String REDO_NONE = "voyager.setup.redo.none";
    private static final String IMPORT_STARTED = "voyager.setup.import.started";
    private static final String IMPORT_DONE = "voyager.setup.import.done";
    private static final String IMPORT_FAILED = "voyager.setup.import.failed";
    private static final String IMPORT_BUSY = "voyager.setup.import.busy";
    private static final String IMPORT_ROOT_MISSING = "voyager.setup.import.root.missing";
    private static final String IMPORT_FOLDER_INVALID = "voyager.setup.import.folder.invalid";
    private static final String IMPORT_FOLDER_MISSING = "voyager.setup.import.folder.missing";
    private static final String IMPORT_LAYOUT_UNSUPPORTED = "voyager.setup.import.layout.unsupported";
    private static final String IMPORT_LAYOUT_AMBIGUOUS = "voyager.setup.import.layout.ambiguous";
    private static final String IMPORT_LINK_REFUSED = "voyager.setup.import.link.refused";
    private static final String IMPORT_TOO_LARGE = "voyager.setup.import.too.large";
    private static final String IMPORT_DATA_VERSION_NEWER = "voyager.setup.import.dataversion.newer";
    private static final String IMPORT_SPAWN_FOUND = "voyager.setup.import.spawn.found";
    private static final String IMPORT_SPAWN_NONE = "voyager.setup.import.spawn.none";
    private static final String IMPORT_SPAWN_HINT = "voyager.setup.import.spawn.hint";
    private static final String IMPORT_CENSUS = "voyager.setup.import.census";
    private static final String IMPORT_PARTIAL_WARNING = "voyager.setup.import.partial.warning";
    private static final String IMPORT_DIMENSIONS_IGNORED = "voyager.setup.import.dimensions.ignored";

    private SetupMessages() {
    }

    @Contract(pure = true, value = "_ -> new")
    public static Component invalidId(String id) {
        return Component.translatable(ID_INVALID, Component.text(id));
    }

    @Contract(pure = true, value = "_ -> new")
    public static Component created(String id) {
        return Component.translatable(MAP_CREATED, Component.text(id));
    }

    @Contract(pure = true, value = "_ -> new")
    public static Component alreadyExists(String id) {
        return Component.translatable(MAP_EXISTS, Component.text(id));
    }

    @Contract(pure = true, value = "_, _ -> new")
    public static Component opened(String id, int ringCount) {
        return Component.translatable(MAP_OPENED, Component.text(id), Component.text(ringCount));
    }

    @Contract(pure = true)
    public static Component noMapOpen() {
        return Component.translatable(MAP_NONE);
    }

    /** A refusal that names what was refused, such as a file, in the words of the exception that refused it. */
    @Contract(pure = true, value = "_ -> new")
    public static Component refused(String detail) {
        return Component.translatable(REFUSED, Component.text(detail));
    }

    @Contract(pure = true)
    public static Component playerOnly() {
        return Component.translatable(PLAYER_ONLY);
    }

    /**
     * The setup wand instruction: two short chat lines, in the order a builder reads them. The placing line comes first,
     * because the wand is used to place before it is used to remove.
     */
    @Contract(pure = true, value = "-> new")
    public static List<Component> wandInstructions() {
        return List.of(
                Component.translatable(WAND_GIVEN_PLACE),
                Component.translatable(WAND_GIVEN_REMOVE));
    }

    @Contract(pure = true, value = "_, _, _ -> new")
    public static Component spawnSaved(double x, double y, double z) {
        return Component.translatable(SPAWN_SAVED, Component.text(x), Component.text(y), Component.text(z));
    }

    @Contract(pure = true)
    public static Component standInTheMap() {
        return Component.translatable(SPAWN_ELSEWHERE);
    }

    @Contract(pure = true, value = "_ -> new")
    public static Component saveFailed(String id) {
        return Component.translatable(SAVE_FAILED, Component.text(id));
    }

    @Contract(pure = true, value = "_ -> new")
    public static Component statusSpawn(boolean set) {
        return Component.translatable(set ? STATUS_SPAWN_SET : STATUS_SPAWN_UNSET);
    }

    @Contract(pure = true, value = "_ -> new")
    public static Component statusRings(int count) {
        return Component.translatable(STATUS_RINGS, Component.text(count));
    }

    @Contract(pure = true)
    public static Component statusProblemSpawn() {
        return Component.translatable(STATUS_PROBLEM_SPAWN);
    }

    @Contract(pure = true)
    public static Component statusProblemRings() {
        return Component.translatable(STATUS_PROBLEM_RINGS);
    }

    @Contract(pure = true, value = "_ -> new")
    public static Component ringPlaced(int index) {
        return Component.translatable(RING_PLACED, Component.text(index));
    }

    @Contract(pure = true)
    public static Component ringPoseRefused() {
        return Component.translatable(RING_POSE_REFUSED);
    }

    @Contract(pure = true, value = "_ -> new")
    public static Component ringRemoved(int index) {
        return Component.translatable(RING_REMOVED, Component.text(index));
    }

    @Contract(pure = true)
    public static Component ringNone() {
        return Component.translatable(RING_NONE);
    }

    @Contract(pure = true, value = "_ -> new")
    public static Component undoDone(int ringCount) {
        return Component.translatable(UNDO_DONE, Component.text(ringCount));
    }

    @Contract(pure = true)
    public static Component undoNone() {
        return Component.translatable(UNDO_NONE);
    }

    @Contract(pure = true, value = "_ -> new")
    public static Component redoDone(int ringCount) {
        return Component.translatable(REDO_DONE, Component.text(ringCount));
    }

    @Contract(pure = true)
    public static Component redoNone() {
        return Component.translatable(REDO_NONE);
    }

    @Contract(pure = true, value = "_, _ -> new")
    public static Component importStarted(String folder, String id) {
        return Component.translatable(IMPORT_STARTED, Component.text(folder), Component.text(id));
    }

    @Contract(pure = true, value = "_, _, _ -> new")
    public static Component importDone(String id, int regionFiles, long regionBytes) {
        return Component.translatable(IMPORT_DONE, Component.text(id), Component.text(regionFiles),
                Component.text(regionBytes));
    }

    @Contract(pure = true, value = "_, _ -> new")
    public static Component importFailed(String id, String detail) {
        return Component.translatable(IMPORT_FAILED, Component.text(id), Component.text(detail));
    }

    @Contract(pure = true, value = "_ -> new")
    public static Component importBusy(String id) {
        return Component.translatable(IMPORT_BUSY, Component.text(id));
    }

    @Contract(pure = true, value = "_ -> new")
    public static Component importRootMissing(String absolutePath) {
        return Component.translatable(IMPORT_ROOT_MISSING, Component.text(absolutePath));
    }

    @Contract(pure = true, value = "_ -> new")
    public static Component importFolderInvalid(String folder) {
        return Component.translatable(IMPORT_FOLDER_INVALID, Component.text(folder));
    }

    @Contract(pure = true, value = "_ -> new")
    public static Component importFolderMissing(String absolutePath) {
        return Component.translatable(IMPORT_FOLDER_MISSING, Component.text(absolutePath));
    }

    @Contract(pure = true)
    public static Component importLayoutUnsupported() {
        return Component.translatable(IMPORT_LAYOUT_UNSUPPORTED);
    }

    @Contract(pure = true, value = "_, _ -> new")
    public static Component importLayoutAmbiguous(String rootFolder, String dimensionFolder) {
        return Component.translatable(IMPORT_LAYOUT_AMBIGUOUS, Component.text(rootFolder), Component.text(dimensionFolder));
    }

    @Contract(pure = true, value = "_ -> new")
    public static Component importLinkRefused(String fileName) {
        return Component.translatable(IMPORT_LINK_REFUSED, Component.text(fileName));
    }

    @Contract(pure = true, value = "_, _, _ -> new")
    public static Component importTooLarge(String measured, String limit, String settingName) {
        return Component.translatable(IMPORT_TOO_LARGE, Component.text(measured), Component.text(limit),
                Component.text(settingName));
    }

    @Contract(pure = true, value = "_, _ -> new")
    public static Component importDataVersionNewer(String found, String supported) {
        return Component.translatable(IMPORT_DATA_VERSION_NEWER, Component.text(found), Component.text(supported));
    }

    @Contract(pure = true, value = "_, _, _, _ -> new")
    public static Component importSpawnFound(int x, int y, int z, String source) {
        return Component.translatable(IMPORT_SPAWN_FOUND, Component.text(x), Component.text(y), Component.text(z),
                Component.text(source));
    }

    @Contract(pure = true)
    public static Component importSpawnNone() {
        return Component.translatable(IMPORT_SPAWN_NONE);
    }

    @Contract(pure = true, value = "_, _, _ -> new")
    public static Component importSpawnHint(int x, int y, int z) {
        return Component.translatable(IMPORT_SPAWN_HINT, Component.text(x), Component.text(y), Component.text(z));
    }

    @Contract(pure = true, value = "_, _, _, _ -> new")
    public static Component importCensus(int full, int partial, int withoutStatus, int unreadable) {
        return Component.translatable(IMPORT_CENSUS, Component.text(full), Component.text(partial),
                Component.text(withoutStatus), Component.text(unreadable));
    }

    @Contract(pure = true, value = "_, _ -> new")
    public static Component importPartialWarning(int partial, int percent) {
        return Component.translatable(IMPORT_PARTIAL_WARNING, Component.text(partial), Component.text(percent));
    }

    @Contract(pure = true, value = "_ -> new")
    public static Component importDimensionsIgnored(String names) {
        return Component.translatable(IMPORT_DIMENSIONS_IGNORED, Component.text(names));
    }
}
