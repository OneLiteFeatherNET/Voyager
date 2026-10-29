package net.elytrarace.voyager.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.GuideParticle;
import net.elytrarace.voyager.platform.catalog.CatalogLoader;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The guide particle the shipped maps name, read from their files. The loaded course cannot say whether a particle
 * was named or defaulted, so the first test reads the JSON text itself.
 *
 * <p>Reads only {@code src/main/resources}. Each test is one behaviour, writes nothing and keeps no state.
 */
class ShippedGuideParticleTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Path MAPS = RESOURCES.resolve("maps");
    private static final String PALM_BEACH = "elytrarace:palm_beach";
    private static final String DRAGON_LOBBY = "elytrarace:dragon_lobby";
    private static final String BLUE_AND_RED = "elytrarace:blue_and_red";
    private static final String MEMORIA = "elytrarace:memoria";

    @Test
    void everyShippedMapNamesItsParticleExplicitlyAndEveryDustMapNamesAColour() throws IOException {
        List<Path> files;
        try (Stream<Path> listed = Files.list(MAPS)) {
            files = listed.filter(path -> path.toString().endsWith(".json")).sorted().toList();
        }

        for (Path file : files) {
            JsonObject line = JsonParser.parseString(Files.readString(file))
                    .getAsJsonObject().getAsJsonObject("guideLine");

            assertThat(line.has("particle"))
                    .as("guideLine.particle is named in %s", file.getFileName())
                    .isTrue();
            String particle = line.get("particle").getAsString();
            assertThat(particle)
                    .as("guideLine.particle of %s", file.getFileName())
                    .isIn("END_ROD", "DUST");
            if ("DUST".equals(particle)) {
                assertThat(line.has("dustColor"))
                        .as("guideLine.dustColor is named in %s, which is DUST", file.getFileName())
                        .isTrue();
            }
        }
    }

    @Test
    void palmBeachIsDustInDeepVioletAndTheOtherThreeShippedMapsAreEndRod() {
        CatalogSnapshot catalog = CatalogLoader.load(RESOURCES);

        GuideLine palmBeach = catalog.mapByName(PALM_BEACH).orElseThrow().guideLine();
        assertThat(palmBeach.particle()).as("particle of %s", PALM_BEACH).isEqualTo(GuideParticle.DUST);
        assertThat(palmBeach.dustRgb()).as("dustColor of %s", PALM_BEACH).isEqualTo(0x7A1FB8);

        for (String other : List.of(DRAGON_LOBBY, BLUE_AND_RED, MEMORIA)) {
            GuideLine line = catalog.mapByName(other).orElseThrow().guideLine();
            assertThat(line.particle()).as("particle of %s", other).isEqualTo(GuideParticle.END_ROD);
        }
    }
}
