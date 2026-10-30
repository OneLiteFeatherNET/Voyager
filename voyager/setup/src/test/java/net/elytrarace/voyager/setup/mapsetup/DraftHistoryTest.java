package net.elytrarace.voyager.setup.mapsetup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The undo history of one open map. Plain JUnit: no Minestom, no file, no clock. Every draft comes from
 * {@link DraftEditor}, so each test steps through the edits the builder really makes.
 */
class DraftHistoryTest {

    private static final MapId ID = new MapId("skyfortress");

    private static MapDraft skeleton() {
        return DraftEditor.skeleton(ID);
    }

    private static Ring ringAt(int index, double z) {
        return new Ring(index, new Vec3(0, 64, z), new Vec3(0, 0, 1), RingDefaults.RADIUS, RingDefaults.POINTS,
                RingType.STANDARD);
    }

    /** A draft of three rings at z = 0, 10 and 20. */
    private static MapDraft threeRings() {
        MapDraft draft = skeleton();
        for (double z : new double[] {0, 10, 20}) {
            draft = DraftEditor.withRingAppended(draft, ringAt(draft.rings().size(), z));
        }
        return draft;
    }

    @Test
    void aNewHistoryHasNothingToUndo() {
        assertThat(DraftHistory.start(skeleton()).canUndo()).isFalse();
    }

    @Test
    void undoAfterOneRecordedEditReturnsTheDraftBeforeIt() {
        MapDraft before = skeleton();
        MapDraft after = DraftEditor.withRingAppended(before, ringAt(0, 0));

        DraftHistory undone = DraftHistory.start(before).record(after).undo();

        assertThat(undone.current()).isEqualTo(before);
    }

    @Test
    void undoAfterAnAppendedRingRestoresTheRingsBeforeIt() {
        MapDraft before = threeRings();
        MapDraft after = DraftEditor.withRingAppended(before, ringAt(3, 30));

        DraftHistory undone = DraftHistory.start(before).record(after).undo();

        assertThat(undone.current().rings()).hasSize(3);
        assertThat(undone.current().rings()).isEqualTo(before.rings());
    }

    @Test
    void undoAfterARemovedRingRestoresTheRemovedRingAtItsOriginalIndex() {
        MapDraft before = threeRings();
        MapDraft after = DraftEditor.withoutRing(before, 1);

        DraftHistory undone = DraftHistory.start(before).record(after).undo();

        assertThat(undone.current().rings()).extracting(Ring::index).containsExactly(0, 1, 2);
        assertThat(undone.current().rings().get(1)).isEqualTo(before.rings().get(1));
        assertThat(undone.current().rings().get(2)).isEqualTo(before.rings().get(2));
    }

    @Test
    void redoAfterAnUndoReappliesTheUndoneEdit() {
        MapDraft before = skeleton();
        MapDraft after = DraftEditor.withRingAppended(before, ringAt(0, 0));
        DraftHistory undone = DraftHistory.start(before).record(after).undo();

        DraftHistory redone = undone.redo();

        assertThat(redone.current()).isEqualTo(after);
        assertThat(redone.canRedo()).isFalse();
    }

    @Test
    void aNewEditAfterAnUndoClearsTheRedoHistory() {
        MapDraft before = skeleton();
        MapDraft first = DraftEditor.withRingAppended(before, ringAt(0, 0));
        MapDraft second = DraftEditor.withRingAppended(before, ringAt(0, 5));
        DraftHistory undone = DraftHistory.start(before).record(first).undo();

        DraftHistory branched = undone.record(second);

        assertThat(branched.canRedo()).isFalse();
        assertThat(branched.current()).isEqualTo(second);
    }

    @Test
    void afterOneHundredAndOneEditsOnlyTheOneHundredNewestEarlierDraftsCanBeUndone() {
        MapDraft draft = skeleton();
        DraftHistory history = DraftHistory.start(draft);
        for (int edit = 1; edit <= 101; edit++) {
            draft = DraftEditor.withSpawn(draft, new Vec3(edit, 64, 0));
            history = history.record(draft);
        }

        for (int undo = 0; undo < 100; undo++) {
            history = history.undo();
        }

        assertThat(history.current().spawn()).isEqualTo(new Vec3(1, 64, 0));
        assertThat(history.canUndo()).isFalse();
    }

    @Test
    void undoWithNothingToUndoIsAProgrammingError() {
        DraftHistory fresh = DraftHistory.start(skeleton());

        assertThatThrownBy(fresh::undo).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aRecordedEditHasNothingToRedoUntilAnUndoHappens() {
        MapDraft before = skeleton();
        DraftHistory recorded = DraftHistory.start(before).record(DraftEditor.withSpawn(before, new Vec3(1, 64, 0)));

        assertThat(recorded.canRedo()).isFalse();
        assertThat(recorded.canUndo()).isTrue();
    }
}
