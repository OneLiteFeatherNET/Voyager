package net.elytrarace.voyager.setup.mapsetup;

import net.elytrarace.voyager.api.mapsetup.DraftStore;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.mapsetup.exception.DraftAlreadyExistsException;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportGuardedDraftStoreTest {

    private final MapId skycube = new MapId("skycube");
    private final ImportGuard guard = new ImportGuard();
    private final RecordingStore delegate = new RecordingStore();
    private final ImportGuardedDraftStore store = new ImportGuardedDraftStore(delegate, guard);

    @Test
    void aCreateOfAnIdWhoseImportIsRunningIsRefusedAsAnExistingDraft() {
        guard.claim(skycube);

        assertThatThrownBy(() -> store.create(DraftEditor.skeleton(skycube)))
                .isInstanceOf(DraftAlreadyExistsException.class);
        assertThat(delegate.created).as("the delegate is not asked to create the world").isEmpty();
    }

    @Test
    void aCreateOfAFreeIdGoesToTheDelegate() {
        store.create(DraftEditor.skeleton(skycube));

        assertThat(delegate.created).hasSize(1);
    }

    /** A store that remembers what was asked of it. */
    private static final class RecordingStore implements DraftStore {

        final List<MapDraft> created = new ArrayList<>();

        @Override
        public MapDraft create(MapDraft skeleton) {
            created.add(skeleton);
            return skeleton;
        }

        @Override
        public MapDraft load(MapId id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void save(MapDraft draft) {
            throw new UnsupportedOperationException();
        }
    }
}
