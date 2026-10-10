package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.catalog.exception.UnresolvedCupMapException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoadedCatalogTest {

    @Test
    void rotationFollowsTheCupsOrderNotTheSnapshots() {
        MapDefinition first = CatalogFixtures.mapNamed("alpha");
        MapDefinition second = CatalogFixtures.mapNamed("beta");
        CupDefinition cup = CatalogFixtures.cupNamed("tour", "beta", "alpha");
        LoadedCatalog loaded = new LoadedCatalog(
                new CatalogSnapshot(Map.of("alpha", first, "beta", second), Map.of("tour", cup)),
                cup, Instant.EPOCH);

        assertThat(loaded.rotation()).containsExactly(second, first);
    }

    @Test
    void aCupNamingAMapTheSnapshotDoesNotHoldIsRefusedWhenTheCatalogueIsBuilt() {
        MapDefinition alpha = CatalogFixtures.mapNamed("alpha");
        CupDefinition cup = CatalogFixtures.cupNamed("tour", "alpha", "missing");

        assertThatThrownBy(() -> new LoadedCatalog(
                new CatalogSnapshot(Map.of("alpha", alpha), Map.of("tour", cup)), cup, Instant.EPOCH))
                .isInstanceOf(UnresolvedCupMapException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void theRotationOfACupIsTheMapsItNamesInOrder() {
        MapDefinition alpha = CatalogFixtures.mapNamed("alpha");
        CupDefinition cup = CatalogFixtures.cupNamed("solo", "alpha");
        LoadedCatalog loaded = new LoadedCatalog(
                new CatalogSnapshot(Map.of("alpha", alpha), Map.of("solo", cup)), cup, Instant.EPOCH);

        assertThat(loaded.rotation()).isEqualTo(List.of(alpha));
    }
}
