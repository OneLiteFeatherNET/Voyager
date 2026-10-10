package net.elytrarace.setup.util;

import net.elytrarace.common.map.model.FilePortalDTO;
import net.elytrarace.common.map.model.LocationDTO;
import net.elytrarace.common.map.model.PortalDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the auto-index calculation logic used by PortalCommand.
 * Mirrors FaweHelper.nextPortalIndex() but doesn't load FAWE classes.
 */
class PortalIndexTest {

    /**
     * Same logic as FaweHelper.nextPortalIndex — duplicated here to avoid
     * loading FAWE classes in test classpath.
     */
    private static int nextPortalIndex(Collection<? extends PortalDTO> portals) {
        return portals.stream()
                .mapToInt(PortalDTO::index)
                .max()
                .orElse(0) + 1;
    }

    @Test
    @DisplayName("Returns 1 for empty portal set")
    void shouldReturnOneForEmptyPortals() {
        assertThat(nextPortalIndex(new TreeSet<>())).isEqualTo(1);
    }

    @Test
    @DisplayName("Returns max+1 for sequential portals [1,2,3] -> 4")
    void shouldReturnNextAfterMaxForSequentialPortals() {
        Set<PortalDTO> portals = new TreeSet<>();
        portals.add(new FilePortalDTO(1, List.of()));
        portals.add(new FilePortalDTO(2, List.of()));
        portals.add(new FilePortalDTO(3, List.of()));

        assertThat(nextPortalIndex(portals)).isEqualTo(4);
    }

    @Test
    @DisplayName("Returns max+1 even with gaps [1,2,5] -> 6")
    void shouldReturnNextAfterMaxWithGaps() {
        Set<PortalDTO> portals = new TreeSet<>();
        portals.add(new FilePortalDTO(1, List.of()));
        portals.add(new FilePortalDTO(2, List.of()));
        portals.add(new FilePortalDTO(5, List.of()));

        assertThat(nextPortalIndex(portals)).isEqualTo(6);
    }

    @Test
    @DisplayName("Handles single portal [1] -> 2")
    void shouldHandleSinglePortal() {
        Set<PortalDTO> portals = new TreeSet<>();
        portals.add(new FilePortalDTO(1, List.of(new LocationDTO(0, 0, 0, true))));

        assertThat(nextPortalIndex(portals)).isEqualTo(2);
    }

    @Test
    @DisplayName("Handles large index [100] -> 101")
    void shouldHandleLargeIndex() {
        Set<PortalDTO> portals = new TreeSet<>();
        portals.add(new FilePortalDTO(100, List.of()));

        assertThat(nextPortalIndex(portals)).isEqualTo(101);
    }

    // --- Duplicate detection tests (mirrors FaweHelper.findOverlappingPortal) ---

    private static int findOverlappingPortal(Collection<? extends PortalDTO> existing,
                                             List<LocationDTO> newLocations, double minDistance) {
        var newCenter = newLocations.stream().filter(LocationDTO::center).findFirst().orElse(null);
        if (newCenter == null) return -1;
        for (var portal : existing) {
            var existingCenter = portal.locations().stream()
                    .filter(LocationDTO::center).findFirst().orElse(null);
            if (existingCenter == null) continue;
            double dx = newCenter.x() - existingCenter.x();
            double dy = newCenter.y() - existingCenter.y();
            double dz = newCenter.z() - existingCenter.z();
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) < minDistance) return portal.index();
        }
        return -1;
    }

    @Test
    @DisplayName("Detects overlapping portal within distance")
    void shouldDetectOverlap() {
        Set<PortalDTO> existing = new TreeSet<>();
        existing.add(new FilePortalDTO(1, List.of(new LocationDTO(100, 50, 100, true))));

        var newLocations = List.of(new LocationDTO(101, 50, 100, true)); // 1 block away

        assertThat(findOverlappingPortal(existing, newLocations, 3.0)).isEqualTo(1);
    }

    @Test
    @DisplayName("No overlap when portals are far apart")
    void shouldNotDetectOverlapWhenFar() {
        Set<PortalDTO> existing = new TreeSet<>();
        existing.add(new FilePortalDTO(1, List.of(new LocationDTO(100, 50, 100, true))));

        var newLocations = List.of(new LocationDTO(200, 50, 100, true)); // 100 blocks away

        assertThat(findOverlappingPortal(existing, newLocations, 3.0)).isEqualTo(-1);
    }

    @Test
    @DisplayName("No overlap with empty existing portals")
    void shouldNotDetectOverlapWithEmpty() {
        var newLocations = List.of(new LocationDTO(100, 50, 100, true));

        assertThat(findOverlappingPortal(new TreeSet<>(), newLocations, 3.0)).isEqualTo(-1);
    }

    @Test
    @DisplayName("No overlap when new portal has no center")
    void shouldHandleNoCenterInNew() {
        Set<PortalDTO> existing = new TreeSet<>();
        existing.add(new FilePortalDTO(1, List.of(new LocationDTO(100, 50, 100, true))));

        var newLocations = List.of(new LocationDTO(100, 50, 100, false)); // no center

        assertThat(findOverlappingPortal(existing, newLocations, 3.0)).isEqualTo(-1);
    }
}
