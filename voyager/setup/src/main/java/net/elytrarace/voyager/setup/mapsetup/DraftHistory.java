package net.elytrarace.voyager.setup.mapsetup;

import net.elytrarace.voyager.api.mapsetup.MapDraft;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.List;

/**
 * The drafts an open map has been through, for undo and redo. It is a value: each operation returns a new history, and
 * the caller keeps the new one only after the save of its current draft has succeeded, so a failed save keeps the old
 * history. It holds no clock, no file and no Minestom type.
 *
 * <p>The history stores whole drafts, not the inverse of each edit: a removed ring renumbers the rings after it, and only
 * the draft from before the removal restores them exactly.
 *
 * @param current the draft the builder sees now
 * @param earlier the drafts before the current one, oldest first, at most {@link #LIMIT} of them
 * @param undone  the drafts that undo took back, the one to re-apply first at the head
 */
@ApiStatus.Internal
public record DraftHistory(MapDraft current, List<MapDraft> earlier, List<MapDraft> undone) {

    /** The most earlier drafts one open map keeps; the oldest is dropped first. */
    public static final int LIMIT = 100;

    public DraftHistory {
        earlier = List.copyOf(earlier);
        undone = List.copyOf(undone);
    }

    /**
     * @param draft the draft the map was opened with
     * @return a history with that draft and nothing to undo or redo
     */
    @Contract(pure = true, value = "_ -> new")
    public static DraftHistory start(MapDraft draft) {
        return new DraftHistory(draft, List.of(), List.of());
    }

    /**
     * Records an edit: the current draft becomes an earlier one, and the redo history is cleared, because a new edit
     * starts a new branch.
     *
     * @param next the draft the edit produced
     * @return the history with {@code next} as the current draft
     */
    @Contract(pure = true, value = "_ -> new")
    public DraftHistory record(MapDraft next) {
        return new DraftHistory(next, withCurrentAppended(earlier), List.of());
    }

    /**
     * @return the history with the draft before the current one as the current draft; the current draft is kept for redo
     * @throws IllegalStateException if there is no earlier draft; check {@link #canUndo()} first
     */
    @Contract(pure = true, value = "-> new")
    public DraftHistory undo() {
        if (!canUndo()) {
            throw new IllegalStateException("no earlier draft to undo to");
        }
        List<MapDraft> kept = new ArrayList<>(earlier);
        MapDraft previous = kept.removeLast();
        List<MapDraft> taken = new ArrayList<>(undone.size() + 1);
        taken.add(current);
        taken.addAll(undone);
        return new DraftHistory(previous, kept, taken);
    }

    /**
     * @return the history with the most recently undone draft as the current draft again
     * @throws IllegalStateException if nothing was undone; check {@link #canRedo()} first
     */
    @Contract(pure = true, value = "-> new")
    public DraftHistory redo() {
        if (!canRedo()) {
            throw new IllegalStateException("no undone draft to redo");
        }
        return new DraftHistory(undone.getFirst(), withCurrentAppended(earlier),
                undone.subList(1, undone.size()));
    }

    /** @return whether {@link #undo()} has an earlier draft to return to */
    public boolean canUndo() {
        return !earlier.isEmpty();
    }

    /** @return whether {@link #redo()} has an undone draft to re-apply */
    public boolean canRedo() {
        return !undone.isEmpty();
    }

    /** The earlier drafts with the current one appended, the oldest dropped when the list would pass the limit. */
    private List<MapDraft> withCurrentAppended(List<MapDraft> drafts) {
        List<MapDraft> grown = new ArrayList<>(drafts);
        grown.add(current);
        if (grown.size() > LIMIT) {
            grown.removeFirst();
        }
        return grown;
    }
}
