package net.elytrarace.voyager.server;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.platform.catalog.CatalogLoader;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The bar the first closed alpha is judged against, stated in code over the data the server boots with.
 *
 * <p>Reads only {@code src/main/resources} through the real {@link CatalogLoader}. Each test is one behaviour, writes
 * nothing, and keeps no state, so the order they run in cannot change a result.
 */
class ShippedCatalogueTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final String ALPHA_CUP = "alpha_cup";
    private static final String ALPHA_MAP = "elytraraceblueandred";

    @Test
    void theShippedCatalogueHoldsExactlyOneCupNamedAlphaCup() {
        CatalogSnapshot catalog = CatalogLoader.load(RESOURCES);

        assertThat(catalog.cupNames())
                .as("the cups the shipped catalogue holds")
                .containsExactly(ALPHA_CUP);
    }

    @Test
    void theAlphaCupPlaysOnlyTheElytraraceBlueAndRedMap() {
        CupDefinition cup = CatalogLoader.load(RESOURCES).cupByName(ALPHA_CUP).orElseThrow();

        assertThat(cup.mapNames())
                .as("the maps alpha_cup plays, in order")
                .containsExactly(ALPHA_MAP);
    }

    @Test
    void theShippedMapHasAtLeastEightRingsIndexedFromZero() {
        MapDefinition map = CatalogLoader.load(RESOURCES).mapByName(ALPHA_MAP).orElseThrow();

        assertThat(map.rings())
                .as("the rings of %s", ALPHA_MAP)
                .hasSizeGreaterThanOrEqualTo(8);
        assertThat(map.rings()).extracting(Ring::index)
                .containsExactlyElementsOf(IntStream.range(0, map.rings().size()).boxed().toList());
    }

    @Test
    void theShippedMapCarriesAtLeastTwoBoostRings() {
        MapDefinition map = CatalogLoader.load(RESOURCES).mapByName(ALPHA_MAP).orElseThrow();

        List<Ring> boosts = map.rings().stream().filter(ring -> ring.type() == RingType.BOOST).toList();

        assertThat(boosts)
                .as("the BOOST rings of %s", ALPHA_MAP)
                .hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void theShippedCatalogueLoadsWithoutAnyProblem() {
        assertThatCode(() -> CatalogLoader.load(RESOURCES))
                .as("loading the shipped catalogue must report no problem")
                .doesNotThrowAnyException();
    }
}
