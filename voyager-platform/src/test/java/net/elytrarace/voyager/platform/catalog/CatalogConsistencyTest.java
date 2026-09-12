package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.MapCatalog;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.platform.catalog.exception.UnresolvedCupMapException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogConsistencyTest {

    /**
     * The maps side is the {@code MapCatalog} port, supplied here as a lambda. That is not a
     * shortcut: it is the assertion that the check needs nothing from a map catalogue but the
     * ability to answer a name, so a future catalogue backed by a database satisfies it unchanged.
     */
    private static MapCatalog knowing(String... names) {
        Set<String> known = Set.of(names);
        MapDefinition any = new MapDefinition(
                "ignored", "ignored", Vec3.ZERO,
                List.of(new Ring(0, Vec3.ZERO, new Vec3(0, 1, 0), 3, 10, RingType.STANDARD)),
                Duration.ofSeconds(60));
        return name -> known.contains(name) ? Optional.of(any) : Optional.empty();
    }

    @Test
    void passesWhenEveryCupEntryResolves(@TempDir Path cups) {
        CatalogFixtures.cup(cups, "a.json", "weekly", "RACE", "blue", "sprint");
        CatalogFixtures.cup(cups, "b.json", "casual", "PRACTICE", "sprint");

        assertThatCode(() -> CatalogConsistency.requireEveryCupMapResolves(
                new JsonCupCatalog(cups), knowing("blue", "sprint"))).doesNotThrowAnyException();
    }

    @Test
    void namesEveryUnresolvedEntryAndNotJustTheFirst(@TempDir Path cups) {
        // Two cups, two different missing maps, and one that resolves in between. A check that
        // stopped at the first would report one of these and hide the other, which costs a second
        // boot to find — the exact cost this check exists to avoid.
        CatalogFixtures.cup(cups, "a.json", "weekly", "RACE", "blue", "cathedral");
        CatalogFixtures.cup(cups, "b.json", "casual", "PRACTICE", "sprint");

        assertThatThrownBy(() -> CatalogConsistency.requireEveryCupMapResolves(
                new JsonCupCatalog(cups), knowing("blue")))
                .isInstanceOf(UnresolvedCupMapException.class)
                .hasMessageContaining("cup 'weekly' plays 'cathedral'")
                .hasMessageContaining("cup 'casual' plays 'sprint'")
                .hasMessageContaining("2 cup entries");
    }

    @Test
    void countsOneUnresolvedEntryInTheSingular(@TempDir Path cups) {
        CatalogFixtures.cup(cups, "a.json", "weekly", "RACE", "blue", "cathedral");

        assertThatThrownBy(() -> CatalogConsistency.requireEveryCupMapResolves(
                new JsonCupCatalog(cups), knowing("blue")))
                .hasMessageContaining("1 cup entry does");
    }

    @Test
    void checksEveryMapOfARotationAndNotOnlyTheFirst(@TempDir Path cups) {
        // The missing map is last in the rotation. A check that looked at mapNames().getFirst()
        // would pass here and fail four maps into the night.
        CatalogFixtures.cup(cups, "a.json", "weekly", "RACE", "blue", "sprint", "cathedral");

        assertThatThrownBy(() -> CatalogConsistency.requireEveryCupMapResolves(
                new JsonCupCatalog(cups), knowing("blue", "sprint")))
                .hasMessageContaining("cup 'weekly' plays 'cathedral'");
    }
}
