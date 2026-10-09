package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogConsistencyTest {

    @Test
    void anEmptyDanglingListWhenEveryCupEntryResolves() {
        assertThat(CatalogConsistency.unresolvedCupMaps(
                maps("blue", "sprint"), cups(CatalogFixtures.cupNamed("weekly", "blue", "sprint"))))
                .isEmpty();
    }

    @Test
    void namesEveryUnresolvedEntryAndNotJustTheFirst() {
        // Two cups, two different missing maps, and one that resolves in between. A check that
        // stopped at the first would report one and hide the other, which costs a second boot.
        Map<String, CupDefinition> cups = cups(
                CatalogFixtures.cupNamed("weekly", "blue", "cathedral"),
                CatalogFixtures.cupNamed("casual", "sprint"));

        assertThat(messageOf(maps("blue"), cups))
                .contains("cup 'weekly' plays 'cathedral'")
                .contains("cup 'casual' plays 'sprint'")
                .contains("2 cup entries");
    }

    @Test
    void countsOneUnresolvedEntryInTheSingular() {
        assertThat(messageOf(
                maps("blue"), cups(CatalogFixtures.cupNamed("weekly", "blue", "cathedral"))))
                .contains("1 cup entry does");
    }

    @Test
    void checksEveryMapOfARotationAndNotOnlyTheFirst() {
        // The missing map is last in the rotation. A check that looked at the first map alone would
        // pass here and fail four maps into the night.
        Map<String, CupDefinition> cups = cups(CatalogFixtures.cupNamed("weekly", "blue", "sprint", "cathedral"));

        assertThat(messageOf(maps("blue", "sprint"), cups))
                .contains("cup 'weekly' plays 'cathedral'");
    }

    @Test
    void aSingleDanglingEntryKeepsTodaysExactMessage() {
        // Pinned to the whole message: the number, the verb, the cup and the map are what an
        // operator reads at boot.
        assertThat(messageOf(
                maps("blue"), cups(CatalogFixtures.cupNamed("weekly", "blue", "cathedral"))))
                .isEqualTo("1 cup entry does name a map no map definition provides: cup 'weekly' plays 'cathedral'");
    }

    /** The message of the exception the check raises; fails if the check finds nothing dangling. */
    private static String messageOf(Map<String, MapDefinition> maps, Map<String, CupDefinition> cups) {
        return CatalogConsistency.unresolvedCupMaps(maps, cups).orElseThrow().getMessage();
    }

    private static Map<String, MapDefinition> maps(String... names) {
        Map<String, MapDefinition> maps = new LinkedHashMap<>();
        for (String name : names) {
            maps.put(name, CatalogFixtures.mapNamed(name));
        }
        return maps;
    }

    private static Map<String, CupDefinition> cups(CupDefinition... definitions) {
        Map<String, CupDefinition> cups = new LinkedHashMap<>();
        for (CupDefinition cup : definitions) {
            cups.put(cup.name(), cup);
        }
        return cups;
    }
}
