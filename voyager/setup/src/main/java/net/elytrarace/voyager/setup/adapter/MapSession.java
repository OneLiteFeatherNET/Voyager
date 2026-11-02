package net.elytrarace.voyager.setup.adapter;

import net.elytrarace.voyager.api.mapsetup.DraftStore;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.setup.mapsetup.DraftHistory;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;

/**
 * One builder's open map: the drafts it has been through, the builder who holds it, and the world the builder stands in.
 *
 * <p>Every change goes through {@link #commit}, {@link #undo} or {@link #redo}, which all share one save: the draft is
 * saved first and only then kept, so the draft in memory is always the one on disk. The save is guarded: it is refused
 * when the stored copy is no longer the draft this session holds. Player events run on Minestom's main thread, so a
 * session needs no lock.
 */
public final class MapSession {

    private final Player builder;
    private final DraftStore store;
    private final Instance instance;
    private final RingPreviews previews;
    private DraftHistory history;

    /**
     * @param builder  the builder who holds the map; an unlock moves this player out of the world
     * @param store    the store the draft is saved through
     * @param draft    the draft as last saved
     * @param instance the world the builder stands in; the previews of the draft are shown in it at once
     */
    public MapSession(Player builder, DraftStore store, MapDraft draft, Instance instance) {
        this.builder = builder;
        this.store = store;
        this.history = DraftHistory.start(draft);
        this.instance = instance;
        this.previews = new RingPreviews(instance);
        previews.show(draft.rings());
    }

    public Player builder() {
        return builder;
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
     * @throws net.elytrarace.voyager.api.mapsetup.exception.DraftChangedException if the stored copy changed since this
     *     session loaded or saved it; the draft and the history are then unchanged
     * @throws net.elytrarace.voyager.api.mapsetup.exception.DraftWriteFailedException if the save did not complete; the
     *     draft and the history are then unchanged
     */
    public void commit(MapDraft next) {
        persist(history.record(next));
    }

    /**
     * Saves the draft before the last edit and keeps it. Nothing is changed when there is no such draft.
     *
     * @return whether a draft was restored
     * @throws net.elytrarace.voyager.api.mapsetup.exception.DraftChangedException if the stored copy changed since this
     *     session loaded or saved it; the draft and the history are then unchanged
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
     * @throws net.elytrarace.voyager.api.mapsetup.exception.DraftChangedException if the stored copy changed since this
     *     session loaded or saved it; the draft and the history are then unchanged
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

    /**
     * Saves the candidate's draft over the one this session holds, then keeps the candidate and shows its rings. The
     * guard compares the stored copy with the draft held before the change.
     */
    private void persist(DraftHistory candidate) {
        store.saveIfUnchanged(draft(), candidate.current());
        history = candidate;
        previews.show(history.current().rings());
    }
}
