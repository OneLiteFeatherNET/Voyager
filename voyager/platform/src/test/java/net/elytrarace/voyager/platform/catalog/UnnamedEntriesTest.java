package net.elytrarace.voyager.platform.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UnnamedEntriesTest {

    @Test
    void aPlayedMapWithoutADisplayNameIsReportedByItsKeyAndTheCupIsReportedToo(@TempDir Path data) {
        CatalogFixtures.map(data.resolve("maps"), "blue.json", "blue", "ElytraraceBlueAndRed");
        CatalogFixtures.write(data.resolve("maps"), "named.json", CatalogFixtures.MAP.formatted("named", "ElytraraceBlueAndRed")
                .replaceFirst("\\{", "{\n  \"displayName\": \"Named\","));
        CatalogFixtures.cup(data.resolve("cups"), "cup.json", "test_cup", "RACE", "blue", "named");

        List<String> warnings = UnnamedEntries.warnings(data, CatalogFixtures.cupNamed("test_cup", "blue", "named"));

        assertThat(warnings).hasSize(2)
                .anySatisfy(warning -> assertThat(warning).contains("elytrarace:blue").contains("Blue"))
                .anySatisfy(warning -> assertThat(warning).contains("elytrarace:test_cup").contains("Test Cup"));
    }

    @Test
    void aNamedPlayedMapAndAnUnplayedUnnamedMapAreNotReported(@TempDir Path data) {
        CatalogFixtures.write(data.resolve("maps"), "named.json", CatalogFixtures.MAP.formatted("named", "ElytraraceBlueAndRed")
                .replaceFirst("\\{", "{\n  \"displayName\": \"Named\","));
        CatalogFixtures.map(data.resolve("maps"), "orphan.json", "orphan", "ElytraraceBlueAndRed");
        CatalogFixtures.write(data.resolve("cups"), "cup.json", CatalogFixtures.CUP.formatted("test_cup", "RACE", "\"named\"")
                .replaceFirst("\\{", "{\n  \"displayName\": \"Test Cup\","));

        List<String> warnings = UnnamedEntries.warnings(data, CatalogFixtures.cupNamed("test_cup", "named"));

        assertThat(warnings).isEmpty();
    }
}
