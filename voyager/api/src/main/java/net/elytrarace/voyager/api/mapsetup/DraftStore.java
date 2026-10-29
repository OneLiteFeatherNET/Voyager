package net.elytrarace.voyager.api.mapsetup;

/**
 * The port the setup server saves its drafts through. voyager-platform implements it over the data directory; the
 * setup server calls it and never touches the file system. The implementation decides the folder of a draft from
 * its completeness, and it keeps the two copies of a draft from ever being resolved silently.
 */
public interface DraftStore {

    /**
     * Creates the world folder of a new map from the void template, then its draft. A failure at either step leaves
     * neither behind.
     *
     * @param skeleton the draft with no rings and no spawn, built by the caller
     * @return the draft as stored
     * @throws net.elytrarace.voyager.api.mapsetup.exception.DraftAlreadyExistsException if a draft or a world of that
     *     id already exists
     */
    MapDraft create(MapDraft skeleton);

    /**
     * Loads the draft of a map.
     *
     * @param id the map's id
     * @return the stored draft
     * @throws net.elytrarace.voyager.api.mapsetup.exception.DraftNotFoundException if no draft has that id
     */
    MapDraft load(MapId id);

    /**
     * Saves a draft, replacing its stored copy atomically and moving it to the folder its completeness names.
     *
     * @param draft the draft to store
     * @throws net.elytrarace.voyager.api.mapsetup.exception.DraftWriteFailedException if the write did not complete;
     *     the previously stored copy is then unchanged
     */
    void save(MapDraft draft);

    /**
     * Saves a draft only when its stored copy is still the copy the change was made on. The stored copy and
     * {@code expected} are compared by their canonical JSON, so the layout of the stored file does not matter. A stored
     * copy that cannot be read counts as changed.
     *
     * @param expected the draft as the caller last loaded or saved it
     * @param next     the draft to store in its place
     * @throws net.elytrarace.voyager.api.mapsetup.exception.DraftChangedException if the stored copy differs from
     *     {@code expected} or is missing; the stored copy is then unchanged
     * @throws net.elytrarace.voyager.api.mapsetup.exception.DraftWriteFailedException if the write did not complete
     */
    void saveIfUnchanged(MapDraft expected, MapDraft next);
}
