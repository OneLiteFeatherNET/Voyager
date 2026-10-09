package net.elytrarace.voyager.setup.adapter;

import net.elytrarace.voyager.api.mapsetup.DraftStore;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.minestom.server.instance.Instance;

/**
 * One builder's open map: the last saved draft, and the world the builder stands in.
 *
 * <p>Every change goes through {@link #commit}, which saves first and only then keeps the new draft, so the draft in
 * memory is always the one on disk. Player events run on Minestom's main thread, so a session needs no lock.
 */
public final class MapSession {

    private final DraftStore store;
    private final Instance instance;
    private MapDraft draft;

    /**
     * @param store    the store the draft is saved through
     * @param draft    the draft as last saved
     * @param instance the world the builder stands in
     */
    public MapSession(DraftStore store, MapDraft draft, Instance instance) {
        this.store = store;
        this.draft = draft;
        this.instance = instance;
    }

    public MapId id() {
        return draft.id();
    }

    public MapDraft draft() {
        return draft;
    }

    public Instance instance() {
        return instance;
    }

    /**
     * Saves the next draft and keeps it.
     *
     * @throws net.elytrarace.voyager.api.mapsetup.exception.DraftWriteFailedException if the save did not complete; the
     *     draft kept by this session is then the previous one, which is the one on disk
     */
    public void commit(MapDraft next) {
        store.save(next);
        draft = next;
    }
}
