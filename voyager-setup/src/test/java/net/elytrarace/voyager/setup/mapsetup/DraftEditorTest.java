package net.elytrarace.voyager.setup.mapsetup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DraftEditorTest {

    private static final MapId ID = new MapId("skyfortress");

    @Test
    void theSkeletonHasNoRingsNoSpawnAndTheDefaultTuning() {
        MapDraft draft = DraftEditor.skeleton(ID);

        assertThat(draft.world()).isEqualTo("skyfortress");
        assertThat(draft.spawn()).isNull();
        assertThat(draft.rings()).isEmpty();
        assertThat(draft.referenceTimeSeconds()).isEqualTo(RingDefaults.REFERENCE_TIME_SECONDS);
        assertThat(draft.boostConfig()).isEqualTo(RingDefaults.BOOST);
        assertThat(draft.guideLine()).isEqualTo(RingDefaults.GUIDE_LINE);
    }

    @Test
    void anAppendedRingIsLastAndKeepsItsIndex() {
        MapDraft draft = withRings(2);

        MapDraft appended = DraftEditor.withRingAppended(draft, ring(2, 9));

        assertThat(appended.rings()).hasSize(3);
        assertThat(appended.rings().get(2).index()).isEqualTo(2);
        assertThat(appended.rings().get(2).center()).isEqualTo(new Vec3(9, 0, 0));
    }

    @Test
    void aRingWhoseIndexIsNotTheNextPositionIsRefused() {
        MapDraft draft = withRings(2);

        assertThatThrownBy(() -> DraftEditor.withRingAppended(draft, ring(5, 9)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removingARingRenumbersTheRingsAfterIt() {
        MapDraft draft = withRings(3);

        MapDraft removed = DraftEditor.withoutRing(draft, 1);

        assertThat(removed.rings()).hasSize(2);
        assertThat(removed.rings().get(0).center()).isEqualTo(new Vec3(0, 0, 0));
        assertThat(removed.rings().get(1).index()).isEqualTo(1);
        assertThat(removed.rings().get(1).center()).isEqualTo(new Vec3(4, 0, 0));
    }

    @Test
    void removingAnIndexOutsideTheListIsRefused() {
        MapDraft draft = withRings(2);

        assertThatThrownBy(() -> DraftEditor.withoutRing(draft, 2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void settingTheSpawnReplacesOnlyTheSpawn() {
        MapDraft draft = withRings(1);

        MapDraft placed = DraftEditor.withSpawn(draft, new Vec3(109, -62, 54));

        assertThat(placed.spawn()).isEqualTo(new Vec3(109, -62, 54));
        assertThat(placed.rings()).isEqualTo(draft.rings());
    }

    @Test
    void theInputDraftIsNotChangedByAnEdit() {
        MapDraft draft = withRings(2);

        DraftEditor.withRingAppended(draft, ring(2, 9));
        DraftEditor.withoutRing(draft, 0);
        DraftEditor.withSpawn(draft, Vec3.ZERO);

        assertThat(draft.rings()).hasSize(2);
        assertThat(draft.spawn()).isNull();
    }

    private static MapDraft withRings(int count) {
        MapDraft draft = DraftEditor.skeleton(ID);
        for (int index = 0; index < count; index++) {
            draft = DraftEditor.withRingAppended(draft, ring(index, index * 2));
        }
        return draft;
    }

    private static Ring ring(int index, double x) {
        return new Ring(index, new Vec3(x, 0, 0), new Vec3(0, 0, 1), RingDefaults.RADIUS, 10, RingType.STANDARD);
    }
}
