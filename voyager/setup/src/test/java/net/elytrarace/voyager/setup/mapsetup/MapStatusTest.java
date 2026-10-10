package net.elytrarace.voyager.setup.mapsetup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MapStatusTest {

    @Test
    void anEmptyDraftListsTheMissingSpawnAndTheMissingRings() {
        MapDraft draft = DraftEditor.skeleton(new MapId("skyfortress"));

        MapStatus status = MapStatus.of(draft);

        assertThat(status.spawnSet()).isFalse();
        assertThat(status.ringCount()).isZero();
        assertThat(status.blockingProblems()).containsExactly(MapStatus.Problem.NO_SPAWN, MapStatus.Problem.NO_RINGS);
    }

    @Test
    void aDraftWithASpawnAndRingsListsNoBlockingProblem() {
        List<Ring> rings = new ArrayList<>();
        for (int index = 0; index < 35; index++) {
            rings.add(new Ring(index, new Vec3(index, 64, 0), new Vec3(1, 0, 0), RingDefaults.RADIUS, 10,
                    RingType.STANDARD));
        }
        MapDraft draft = new MapDraft(new MapId("skyfortress"), "skyfortress", new Vec3(0, 64, 0), rings,
                60.0, RingDefaults.BOOST, RingDefaults.GUIDE_LINE);

        MapStatus status = MapStatus.of(draft);

        assertThat(status.spawnSet()).isTrue();
        assertThat(status.ringCount()).isEqualTo(35);
        assertThat(status.blockingProblems()).isEmpty();
    }
}
