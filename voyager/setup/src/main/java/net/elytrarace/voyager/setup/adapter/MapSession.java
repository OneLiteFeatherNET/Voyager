package net.elytrarace.voyager.setup.adapter;

import net.elytrarace.voyager.api.mapsetup.DraftStore;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.setup.mapsetup.DraftHistory;
import net.minestom.server.instance.Instance;

/**
 * One builder's open map: the drafts it has been through, and the world the builder stands in.
 *
 * <p>Every change goes through {@link #commit}, {@link #undo} or {@link #redo}, which all share one save: the draft is
 * saved first and only then kept, so the draft in memory is always the one on disk. Player events run on Minestom's main
 * thread, so a session needs no lock.
 */
public final class MapSession {

    private final DraftStore store;
    private final Instance instance;
    private final RingPreviews previews;
    private DraftHistory history;

    /**
     * @param store    the store the draft is saved through
     * @param draft    the draft as last saved
     * @param instance the world the builder stands in; the previews of the draft are shown in it at once
     */
    public MapSession(DraftStore store, MapDraft draft, Instance instance) {
        this.store = store;
        this.history = DraftHistory.start(draft);
        this.instance = instance;
        this.previews = new RingPreviews(instance);
        previews.show(draft.rings());
    }

    public MapId id() {
        return draft().id();
    }

    public MapDraft draft() {
        return history.current();
    }

    public Instance instance() {
        return instance;
    }

    /**
     * Saves the next draft as an edit and keeps it. The edit clears the redo history.
     *
     * @throws net.elytrarace.voyager.api.mapsetup.exception.DraftWriteFailedException if the save did not complete; the
     *     draft kept by this session is then the previous one, which is the one on disk
     */
    public void commit(MapDraft next) {
        persist(history.record(next));
    }

    /**
     * Saves the draft before the last edit and keeps it. Nothing is changed when there is no such draft.
     *
     * @return whether a draft was restored
     * @throws net.elytrarace.voyager.api.mapsetup.exception.DraftWriteFailedException if the save did not complete; the
     *     draft and the history are then unchanged
     */
    public boolean undo() {
        if (!history.canUndo()) {
            return false;
        }
        persist(history.undo());
        return true;
    }

    /**
     * Saves the draft that the last undo took back and keeps it. Nothing is changed when there is no such draft.
     *
     * @return whether a draft was re-applied
     * @throws net.elytrarace.voyager.api.mapsetup.exception.DraftWriteFailedException if the save did not complete; the
     *     draft and the history are then unchanged
     */
    public boolean redo() {
        if (!history.canRedo()) {
            return false;
        }
        persist(history.redo());
        return true;
    }

    /** Removes the previews of this map from its world; the draft is already saved. */
    public void close() {
        previews.clear();
    }

    /** Saves the candidate's draft, then keeps the candidate and shows its rings. */
    private void persist(DraftHistory candidate) {
        store.save(candidate.current());
        history = candidate;
        previews.show(history.current().rings());
    }
}
