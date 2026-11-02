package net.elytrarace.voyager.setup.mapsetup;

import net.elytrarace.voyager.api.mapsetup.DraftStore;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.mapsetup.exception.DraftAlreadyExistsException;

/**
 * A draft store that refuses {@code /map new} for an id whose import is running, as if the draft already existed. The
 * refusal reuses the store's own "already exists" answer, so the commands need no new branch. Every other call goes to
 * the wrapped store unchanged.
 */
public final class ImportGuardedDraftStore implements DraftStore {

    private final DraftStore delegate;
    private final ImportGuard guard;

    /**
     * @param delegate the store that holds the drafts
     * @param guard    the claims of the imports that are running
     */
    public ImportGuardedDraftStore(DraftStore delegate, ImportGuard guard) {
        this.delegate = delegate;
        this.guard = guard;
    }

    /**
     * @throws DraftAlreadyExistsException if an import of the skeleton's id is running, or the delegate refuses it
     */
    @Override
    public MapDraft create(MapDraft skeleton) {
        if (guard.isClaimed(skeleton.id())) {
            throw DraftAlreadyExistsException.of(skeleton.id());
        }
        return delegate.create(skeleton);
    }

    @Override
    public MapDraft load(MapId id) {
        return delegate.load(id);
    }

    @Override
    public void save(MapDraft draft) {
        delegate.save(draft);
    }

    @Override
    public void saveIfUnchanged(MapDraft expected, MapDraft next) {
        delegate.saveIfUnchanged(expected, next);
    }
}
