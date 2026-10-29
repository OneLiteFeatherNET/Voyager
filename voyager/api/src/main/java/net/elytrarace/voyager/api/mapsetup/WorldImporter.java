package net.elytrarace.voyager.api.mapsetup;

/**
 * The port a finished world is imported through. voyager-platform implements it over the import root and the worlds
 * folder; the setup server calls it and never touches the file system.
 *
 * <p>An import copies the region files of a world into a new world folder of a map, surveys the copy, and keeps the
 * copy only when the survey does not refuse it. The source may change after the import: the copy does not.
 */
public interface WorldImporter {

    /**
     * Copies a world into the world folder of {@code id}, then surveys the copy. A refusal leaves neither the copy nor
     * a folder of that id behind.
     *
     * @param folder the world folder under the import root
     * @param id     the map the world becomes
     * @return what the copy holds
     * @throws net.elytrarace.voyager.api.mapsetup.exception.WorldImportRefusedException if the import is refused
     */
    ImportReport importWorld(WorldFolderName folder, MapId id);

    /**
     * Removes the world folder an earlier {@link #importWorld} created, after the draft of that map could not be saved.
     *
     * @param id the map whose world folder is removed; nothing happens when it does not exist
     */
    void discard(MapId id);
}
