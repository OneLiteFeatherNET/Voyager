package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogSnapshotTest {

    @Test
    void answersAMapByItsName() {
        CatalogSnapshot snapshot = new CatalogSnapshot(
                maps("blue", "sprint"), cups("weekly"));

        assertThat(snapshot.mapByName("elytrarace:blue")).contains(CatalogFixtures.mapNamed("blue"));
    }

    @Test
    void answersAMapNameItDoesNotHoldWithEmpty() {
        CatalogSnapshot snapshot = new CatalogSnapshot(maps("blue"), cups("weekly"));

        assertThat(snapshot.mapByName("cathedral")).isEmpty();
    }

    @Test
    void answersACupByItsName() {
        CatalogSnapshot snapshot = new CatalogSnapshot(maps("blue"), cups("weekly"));

        assertThat(snapshot.cupByName("elytrarace:weekly")).contains(CatalogFixtures.cupNamed("weekly", "elytrarace:blue"));
    }

    @Test
    void answersACupNameItDoesNotHoldWithEmpty() {
        CatalogSnapshot snapshot = new CatalogSnapshot(maps("blue"), cups("weekly"));

        assertThat(snapshot.cupByName("casual")).isEmpty();
    }

    @Test
    void listsTheMapNamesItWasBuiltWithInTheOrderTheyWereGiven() {
        CatalogSnapshot snapshot = new CatalogSnapshot(maps("sprint", "blue", "cathedral"), cups());

        assertThat(snapshot.mapNames()).containsExactly("elytrarace:sprint", "elytrarace:blue", "elytrarace:cathedral");
    }

    @Test
    void listsTheCupNamesItWasBuiltWithInTheOrderTheyWereGiven() {
        CatalogSnapshot snapshot = new CatalogSnapshot(maps(), cups("weekly", "casual"));

        assertThat(snapshot.cupNames()).containsExactly("elytrarace:weekly", "elytrarace:casual");
    }

    @Test
    void refusesAChangeToItsMaps() {
        CatalogSnapshot snapshot = new CatalogSnapshot(maps("blue"), cups());

        assertThatThrownBy(() -> snapshot.maps().put("sprint", CatalogFixtures.mapNamed("sprint")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void refusesAChangeToItsCups() {
        CatalogSnapshot snapshot = new CatalogSnapshot(maps(), cups("weekly"));

        assertThatThrownBy(() -> snapshot.cups().remove("weekly"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void keepsItsContentsWhenTheMapItWasBuiltFromIsChangedAfterwards() {
        Map<String, MapDefinition> source = maps("blue");
        CatalogSnapshot snapshot = new CatalogSnapshot(source, cups());

        source.put("sprint", CatalogFixtures.mapNamed("sprint"));
        source.remove("blue");

        assertThat(snapshot.mapNames()).containsExactly("elytrarace:blue");
    }

    private static Map<String, MapDefinition> maps(String... names) {
        Map<String, MapDefinition> maps = new LinkedHashMap<>();
        for (String name : names) {
            maps.put(name, CatalogFixtures.mapNamed(name));
        }
        return maps;
    }

    private static Map<String, CupDefinition> cups(String... names) {
        Map<String, CupDefinition> cups = new LinkedHashMap<>();
        for (String name : names) {
            cups.put(name, CatalogFixtures.cupNamed(name, "blue"));
        }
        return cups;
    }
}
