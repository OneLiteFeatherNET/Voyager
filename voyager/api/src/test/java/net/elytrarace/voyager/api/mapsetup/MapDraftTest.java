package net.elytrarace.voyager.api.mapsetup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.mapsetup.exception.InvalidDraftException;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MapDraftTest {

    private static final Vec3 EAST = new Vec3(1, 0, 0);

    @Test
    void acceptsADraftWithNoRingsAndNoSpawn() {
        MapDraft draft = draft("skyfortress", "skyfortress", null, List.of(), 60.0);

        assertThat(draft.rings()).isEmpty();
        assertThat(draft.spawn()).isNull();
    }

    @Test
    void refusesABlankWorld() {
        assertThatThrownBy(() -> draft("skyfortress", " ", null, List.of(), 60.0))
                .isInstanceOf(InvalidDraftException.class);
    }

    @Test
    void refusesARingWhoseIndexDiffersFromItsPosition() {
        Ring misplaced = ring(3);

        assertThatThrownBy(() -> draft("skyfortress", "skyfortress", null, List.of(ring(0), misplaced), 60.0))
                .isInstanceOf(InvalidDraftException.class);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY})
    void refusesAReferenceTimeThatIsNotFiniteAndPositive(double seconds) {
        assertThatThrownBy(() -> draft("skyfortress", "skyfortress", null, List.of(), seconds))
                .isInstanceOf(InvalidDraftException.class);
    }

    @Test
    void copiesTheRingListSoALaterChangeToTheInputDoesNotReachTheDraft() {
        List<Ring> rings = new ArrayList<>(List.of(ring(0)));
        MapDraft draft = draft("skyfortress", "skyfortress", null, rings, 60.0);

        rings.add(ring(1));

        assertThat(draft.rings()).hasSize(1);
    }

    private static MapDraft draft(String id, String world, Vec3 spawn, List<Ring> rings, double seconds) {
        return new MapDraft(new MapId(id), world, spawn, rings, seconds,
                new BoostConfig(30, 40), new GuideLine(List.of(), 2, 1.0));
    }

    private static Ring ring(int index) {
        return new Ring(index, new Vec3(0, 64, 0), EAST, 3.605551275463989, 10, RingType.STANDARD);
    }
}
