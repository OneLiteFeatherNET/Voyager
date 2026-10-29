package net.elytrarace.voyager.setup.mapsetup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.race.Author;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.api.race.effect.RingEffectTuning;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

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

    /**
     * An edit rebuilds the draft. A designer's ring factors must come through every edit, or a single spawn placement
     * would reset them to the defaults without a word.
     */
    @Test
    void everyEditKeepsTheRingEffectsTheDraftCarries() {
        RingEffectTuning tuning = new RingEffectTuning(2.0, 0.25);
        MapDraft tuned = new MapDraft(ID, ID.value(), null, List.of(), RingDefaults.REFERENCE_TIME_SECONDS,
                RingDefaults.BOOST, RingDefaults.GUIDE_LINE, tuning);

        MapDraft placed = DraftEditor.withSpawn(tuned, new Vec3(109, -62, 54));
        MapDraft withRing = DraftEditor.withRingAppended(placed, ring(0, 1.0));

        assertThat(placed.ringEffects()).isEqualTo(tuning);
        assertThat(withRing.ringEffects()).isEqualTo(tuning);
    }

    @Test
    void theSkeletonHasTheDefaultRingEffects() {
        assertThat(DraftEditor.skeleton(ID).ringEffects()).isEqualTo(RingEffectTuning.DEFAULT);
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

    @Test
    void appendingARingKeepsTheDisplayNameAndTheAuthors() {
        MapDraft named = withNameAndAuthors(withRings(1));

        MapDraft appended = DraftEditor.withRingAppended(named, ring(1, 2));

        assertThat(appended.displayName()).isEqualTo("Blue & Red");
        assertThat(appended.authors()).isEqualTo(AUTHORS);
    }

    @Test
    void removingARingKeepsTheDisplayNameAndTheAuthors() {
        MapDraft named = withNameAndAuthors(withRings(3));

        MapDraft removed = DraftEditor.withoutRing(named, 1);

        assertThat(removed.displayName()).isEqualTo("Blue & Red");
        assertThat(removed.authors()).isEqualTo(AUTHORS);
    }

    @Test
    void settingTheSpawnKeepsTheDisplayNameAndTheAuthors() {
        MapDraft named = withNameAndAuthors(withRings(1));

        MapDraft placed = DraftEditor.withSpawn(named, new Vec3(109, -62, 54));

        assertThat(placed.displayName()).isEqualTo("Blue & Red");
        assertThat(placed.authors()).isEqualTo(AUTHORS);
    }

    @Test
    void aDraftWithoutANameOrAuthorsStaysWithoutThemAfterAnAppend() {
        MapDraft unnamed = withRings(1);

        MapDraft appended = DraftEditor.withRingAppended(unnamed, ring(1, 2));

        assertThat(appended.displayName()).isNull();
        assertThat(appended.authors()).isEmpty();
    }

    private static final List<Author> AUTHORS = List.of(
            new Author(UUID.fromString("05bf52c6-7bb0-4f13-8951-0e1fd803df35"), "TheMeinerLP"),
            new Author(UUID.fromString("e1dff87a-1059-47f1-bc7d-533f072bf8c7"), "Seelenretterin"));

    private static MapDraft withNameAndAuthors(MapDraft draft) {
        return new MapDraft(draft.id(), draft.world(), draft.spawn(), draft.rings(), draft.referenceTimeSeconds(),
                draft.boostConfig(), draft.guideLine(), draft.ringEffects(), "Blue & Red", AUTHORS);
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
