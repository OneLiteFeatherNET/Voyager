package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.platform.catalog.exception.MalformedCatalogFileException;
import net.elytrarace.voyager.platform.catalog.exception.UnreadableCatalogException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonMapCatalogTest {

    @Test
    void readsAMapUnderTheNameInsideTheFileRatherThanTheFilename(@TempDir Path maps) {
        // The filename and the declared name disagree on purpose: keying by filename would answer
        // byName("elytraraceblueandred") with nothing, which is the failure this class exists to
        // rule out, and a fixture where the two agree could not tell the difference.
        CatalogFixtures.map(maps, "01-first.json", "elytraraceblueandred", "ElytraraceBlueAndRed");

        JsonMapCatalog catalog = new JsonMapCatalog(maps);

        assertThat(catalog.byName("elytraraceblueandred")).isPresent();
        assertThat(catalog.byName("01-first")).isEmpty();
    }

    @Test
    void readsEveryFieldOfADefinitionBack(@TempDir Path maps) {
        // Every value in the fixture differs from every other, so a field read into the wrong slot
        // cannot pass: the spawn's y is -62 where the first ring's centre is at -54, and the two
        // rings differ in centre, normal, radius, score and type.
        CatalogFixtures.map(maps, "map.json", "blue", "ElytraraceBlueAndRed");
        JsonMapCatalog catalog = new JsonMapCatalog(maps);

        MapDefinition map = catalog.byName("blue").orElseThrow();

        assertThat(map.world()).isEqualTo("ElytraraceBlueAndRed");
        assertThat(map.spawn()).isEqualTo(new Vec3(109, -62, 54));
        assertThat(map.referenceTime()).isEqualTo(Duration.ofMillis(46_700));
        assertThat(map.boostConfig()).isEqualTo(new BoostConfig(18, 41));
        assertThat(map.rings()).extracting(Ring::index).containsExactly(0, 1);
        assertThat(map.rings()).extracting(Ring::type).containsExactly(RingType.STANDARD, RingType.BOOST);
        assertThat(map.rings()).extracting(Ring::points).containsExactly(10, 25);
        assertThat(map.rings().get(0).center()).isEqualTo(new Vec3(85, -54, 54));
        assertThat(map.rings().get(0).normal()).isEqualTo(new Vec3(-1, 0, 0));
        assertThat(map.rings().get(0).radius()).isEqualTo(Math.sqrt(13));
        assertThat(map.rings().get(1).center()).isEqualTo(new Vec3(2, -31, 69));
        assertThat(map.rings().get(1).radius()).isEqualTo(3.0);
    }

    @Test
    void keepsEveryMapInTheDirectoryApart(@TempDir Path maps) {
        CatalogFixtures.map(maps, "a.json", "blue", "ElytraraceBlueAndRed");
        CatalogFixtures.map(maps, "b.json", "sprint", "NetherSprint");

        JsonMapCatalog catalog = new JsonMapCatalog(maps);

        assertThat(catalog.mapNames()).containsExactlyInAnyOrder("blue", "sprint");
        assertThat(catalog.byName("blue").orElseThrow().world()).isEqualTo("ElytraraceBlueAndRed");
        assertThat(catalog.byName("sprint").orElseThrow().world()).isEqualTo("NetherSprint");
    }

    @Test
    void answersEmptyForAMapItDoesNotKnow(@TempDir Path maps) {
        CatalogFixtures.map(maps, "a.json", "blue", "ElytraraceBlueAndRed");

        assertThat(new JsonMapCatalog(maps).byName("frozen-cathedral")).isEmpty();
    }

    @Test
    void refusesToBeBuiltFromAFileThatIsNotJson(@TempDir Path maps) {
        CatalogFixtures.write(maps, "broken.json", "{ \"name\": ");

        assertThatThrownBy(() -> new JsonMapCatalog(maps))
                .isInstanceOf(MalformedCatalogFileException.class)
                .hasMessageContaining("broken.json");
    }

    @Test
    void refusesToBeBuiltFromRingsThatAreNotIndexedZeroToNMinusOne(@TempDir Path maps) {
        // A well-formed JSON file whose content a MapDefinition rejects. The exception has to name
        // the file all the same: InvalidMapException on its own says a rotation somewhere is wrong
        // without saying which of thirty files to open.
        CatalogFixtures.write(maps, "out-of-order.json", CatalogFixtures.MAP
                .formatted("blue", "ElytraraceBlueAndRed").replace("\"index\": 1", "\"index\": 7"));

        assertThatThrownBy(() -> new JsonMapCatalog(maps))
                .isInstanceOf(MalformedCatalogFileException.class)
                .hasMessageContaining("out-of-order.json")
                .hasMessageContaining("0..n-1");
    }

    @Test
    void refusesToBeBuiltFromANormalThatIsNotUnitLength(@TempDir Path maps) {
        CatalogFixtures.write(maps, "stretched.json", CatalogFixtures.MAP
                .formatted("blue", "ElytraraceBlueAndRed")
                .replace("\"x\": -1.0, \"y\": 0.0, \"z\": 0.0", "\"x\": -1.4, \"y\": 0.0, \"z\": 0.0"));

        assertThatThrownBy(() -> new JsonMapCatalog(maps))
                .isInstanceOf(MalformedCatalogFileException.class)
                .hasMessageContaining("stretched.json");
    }

    @Test
    void refusesADirectoryThatIsNotThere(@TempDir Path root) {
        assertThatThrownBy(() -> new JsonMapCatalog(root.resolve("maps")))
                .isInstanceOf(UnreadableCatalogException.class)
                .hasMessageContaining("map catalogue directory")
                .hasMessageContaining("does not exist");
    }

    @Test
    void refusesADirectoryWithNoDefinitionsInIt(@TempDir Path maps) {
        CatalogFixtures.write(maps, "readme.txt", "the maps used to live here");

        assertThatThrownBy(() -> new JsonMapCatalog(maps))
                .isInstanceOf(UnreadableCatalogException.class)
                .hasMessageContaining("holds no .json file");
    }
}
