package net.elytrarace.voyager.platform.catalog.schema;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The JSON Schema documents for map and cup files, checked against the committed files and against
 * hand-written fixtures. Test scope only: the loader never consults the schema at runtime.
 *
 * <p>The committed files are read from the module-relative path {@code ../server/src/main/resources},
 * the same deliberate exception {@code CommittedMapDataTest} makes: the test is about the data that ships, so it
 * must read the data that ships. Every other fixture is written inline so the rejection cases are visible in the
 * test that asserts them.
 */
class FileSchemaTest {

    private static final Path SERVER_RESOURCES = Path.of("..", "server", "src", "main", "resources");
    private static final Path PLATFORM_SCHEMAS = Path.of("src", "main", "resources", "schema");
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String VALID_MAP = """
            {
              "$schema": "../schema/map.schema.json",
              "schemaVersion": 2,
              "key": "elytrarace:sky-drift",
              "spawn": { "x": 0.0, "y": 80.0, "z": 0.0 },
              "referenceTimeSeconds": 60.0,
              "boostConfig": { "burnDurationTicks": 30, "cooldownTicks": 40 },
              "guideLine": { "lookAheadRings": 2, "particleSpacing": 1.0, "points": [
                { "orderIndex": 150, "position": { "x": 5.0, "y": 80.0, "z": 0.0 } }
              ] },
              "rings": [
                { "center": { "x": 10.0, "y": 80.0, "z": 0.0 }, "normal": { "x": 1.0, "y": 0.0, "z": 0.0 },
                  "radius": 4.0, "points": 10, "type": "STANDARD" },
                { "index": 1, "center": { "x": 20.0, "y": 81.0, "z": 0.0 }, "normal": { "x": 1.0, "y": 0.0, "z": 0.0 },
                  "radius": 4.0, "points": 10, "type": "BOOST" }
              ],
              "notes": [ "hand-written" ]
            }
            """;

    private static final String VALID_CUP = """
            {
              "$schema": "../schema/cup.schema.json",
              "schemaVersion": 2,
              "key": "elytrarace:sky_cup",
              "mode": "RACE",
              "mapNames": [ "sky-drift" ],
              "notes": [ "hand-written" ]
            }
            """;

    @Test
    void committedMapValidatesAgainstMapSchema() {
        assertThat(errors(mapSchema(), readCommitted("maps/blue_and_red.json"))).isEmpty();
    }

    @Test
    void committedCupValidatesAgainstCupSchema() {
        assertThat(errors(cupSchema(), readCommitted("cups/alpha_cup.json"))).isEmpty();
    }

    @Test
    void committedMapDeclaresSchemaVersionOne() {
        assertThat(readCommitted("maps/blue_and_red.json").get("schemaVersion").asInt()).isEqualTo(2);
    }

    @Test
    void committedCupDeclaresSchemaVersionOne() {
        assertThat(readCommitted("cups/alpha_cup.json").get("schemaVersion").asInt()).isEqualTo(2);
    }

    @Test
    void everyShippedFileResolvesItsSchemaPathToTheSchemaOfItsKind() {
        assertResolvesTo("maps/blue_and_red.json", "map.schema.json");
        assertResolvesTo("cups/alpha_cup.json", "cup.schema.json");
    }

    @Test
    void handWrittenMapWithoutTheDerivedFieldsValidates() {
        // No index on the second ring and no world: both are derived, so the schema must accept them absent.
        assertThat(errors(mapSchema(), JSON.readTree(VALID_MAP))).isEmpty();
    }

    @Test
    void handWrittenCupValidates() {
        assertThat(errors(cupSchema(), JSON.readTree(VALID_CUP))).isEmpty();
    }

    @Test
    void aMapWithAStringDisplayNameValidates() {
        String named = VALID_MAP.replace("\"key\": \"elytrarace:sky-drift\",", "\"key\": \"elytrarace:sky-drift\",\n  \"displayName\": \"Sky Drift\",");

        assertThat(errors(mapSchema(), JSON.readTree(named))).isEmpty();
    }

    @Test
    void aCupWithAStringDisplayNameValidates() {
        String named = VALID_CUP.replace("\"key\": \"elytrarace:sky_cup\",", "\"key\": \"elytrarace:sky_cup\",\n  \"displayName\": \"Sky Cup\",");

        assertThat(errors(cupSchema(), JSON.readTree(named))).isEmpty();
    }

    @Test
    void ringWithoutRadiusIsRejectedNamingRadius() {
        String withoutRadius = VALID_MAP.replace("\"radius\": 4.0, \"points\": 10, \"type\": \"STANDARD\"",
                "\"points\": 10, \"type\": \"STANDARD\"");

        assertThat(errors(mapSchema(), JSON.readTree(withoutRadius))).anySatisfy(
                error -> assertThat(error.getMessage()).contains("radius"));
    }

    @Test
    void ringWithoutCenterIsRejectedNamingCenter() {
        String withoutCenter = VALID_MAP.replace("\"center\": { \"x\": 10.0, \"y\": 80.0, \"z\": 0.0 }, ", "");

        assertThat(errors(mapSchema(), JSON.readTree(withoutCenter))).anySatisfy(
                error -> assertThat(error.getMessage()).contains("center"));
    }

    @Test
    void unknownRingTypeIsRejectedNamingTheValue() {
        String teleport = VALID_MAP.replace("\"type\": \"BOOST\"", "\"type\": \"TELEPORT\"");

        assertThat(errors(mapSchema(), JSON.readTree(teleport))).anySatisfy(
                error -> assertThat(error.getInstanceLocation().toString()).isEqualTo("/rings/1/type"));
    }

    @Test
    void aVersionOneMapWithAFlatNameValidatesAgainstTheVersionOneSchema() {
        String version1 = VALID_MAP.replace("\"schemaVersion\": 2", "\"schemaVersion\": 1")
                .replace("\"key\": \"elytrarace:sky-drift\"", "\"name\": \"sky-drift\"");

        assertThat(errors(mapV1Schema(), JSON.readTree(version1))).isEmpty();
    }

    @Test
    void aVersionOneMapWithAFlatNameIsRefusedByTheVersionTwoSchema() {
        String version1 = VALID_MAP.replace("\"schemaVersion\": 2", "\"schemaVersion\": 1")
                .replace("\"key\": \"elytrarace:sky-drift\"", "\"name\": \"sky-drift\"");

        assertThat(errors(mapSchema(), JSON.readTree(version1))).isNotEmpty();
    }

    @Test
    void aVersionTwoCupWithoutAKeyIsRejectedNamingTheKey() {
        String withoutKey = VALID_CUP.replace("\"key\": \"elytrarace:sky_cup\",", "");

        assertThat(errors(cupSchema(), JSON.readTree(withoutKey))).anySatisfy(
                error -> assertThat(error.getMessage()).contains("key"));
    }

    @Test
    void aKeyInAnotherNamespaceIsRejectedByTheSchema() {
        String otherNamespace = VALID_MAP.replace("elytrarace:sky-drift", "other:sky-drift");

        assertThat(errors(mapSchema(), JSON.readTree(otherNamespace))).anySatisfy(
                error -> assertThat(error.getInstanceLocation().toString()).isEqualTo("/key"));
    }

    @Test
    void anAuthorWithAUuidThatIsNotAUuidIsRejected() {
        String badAuthor = VALID_MAP.replace("\"notes\"", "\"authors\": [ { \"uuid\": \"not-a-uuid\", "
                + "\"name\": \"TheMeinerLP\" } ],\n  \"notes\"");

        assertThat(errors(mapSchema(), JSON.readTree(badAuthor))).anySatisfy(
                error -> assertThat(error.getInstanceLocation().toString()).isEqualTo("/authors/0/uuid"));
    }

    @Test
    void schemaVersionThreeIsOutOfRangeInTheMapSchema() {
        String version3 = VALID_MAP.replace("\"schemaVersion\": 2", "\"schemaVersion\": 3");

        assertThat(errors(mapSchema(), JSON.readTree(version3))).anySatisfy(
                error -> assertThat(error.getInstanceLocation().toString()).isEqualTo("/schemaVersion"));
    }

    @Test
    void theMapSchemaAcceptsRingEffectsAndTheOptionalBoostFields() {
        String tuned = VALID_MAP
                .replace("\"boostConfig\": { \"burnDurationTicks\": 30, \"cooldownTicks\": 40 }",
                        "\"boostConfig\": { \"burnDurationTicks\": 30, \"cooldownTicks\": 40, "
                                + "\"rocketsPerRacer\": 12, \"maxSpeedBlocksPerTick\": 3.5 }")
                .replace("\"guideLine\"", "\"ringEffects\": { \"boostMultiplier\": 2.0, \"slowMultiplier\": 0.25 },\n"
                        + "  \"guideLine\"");

        assertThat(errors(mapSchema(), JSON.readTree(tuned))).isEmpty();
    }

    @Test
    void theMapSchemaRefusesZeroRocketsNamingTheField() {
        String noRockets = VALID_MAP.replace("\"cooldownTicks\": 40 }",
                "\"cooldownTicks\": 40, \"rocketsPerRacer\": 0 }");

        assertThat(errors(mapSchema(), JSON.readTree(noRockets))).anySatisfy(
                error -> assertThat(error.getInstanceLocation().toString()).isEqualTo("/boostConfig/rocketsPerRacer"));
    }

    @Test
    void theMapSchemaRefusesANegativeRingEffectFactor() {
        String negative = VALID_MAP.replace("\"guideLine\"",
                "\"ringEffects\": { \"slowMultiplier\": -0.5 },\n  \"guideLine\"");

        assertThat(errors(mapSchema(), JSON.readTree(negative))).anySatisfy(
                error -> assertThat(error.getInstanceLocation().toString()).isEqualTo("/ringEffects/slowMultiplier"));
    }

    @Test
    void cupModeOutsideTheEnumerationIsRejected() {
        String tournament = VALID_CUP.replace("\"mode\": \"RACE\"", "\"mode\": \"TOURNAMENT\"");

        assertThat(errors(cupSchema(), JSON.readTree(tournament))).anySatisfy(
                error -> assertThat(error.getInstanceLocation().toString()).isEqualTo("/mode"));
    }

    @Test
    void guidePointOnARingsOwnSlotIsRejected() {
        // A multiple of 100 is a ring's slot; GuidePoint refuses it, and the schema says so too.
        String onSlot = VALID_MAP.replace("\"orderIndex\": 150", "\"orderIndex\": 200");

        assertThat(errors(mapSchema(), JSON.readTree(onSlot))).anySatisfy(
                error -> assertThat(error.getInstanceLocation().toString()).isEqualTo("/guideLine/points/0/orderIndex"));
    }

    @Test
    void particleSpacingBelowTheMinimumIsRejected() {
        // GuideLine refuses anything under 0.25 blocks; the schema must not accept what the loader refuses.
        String tooDense = VALID_MAP.replace("\"particleSpacing\": 1.0", "\"particleSpacing\": 0.1");

        assertThat(errors(mapSchema(), JSON.readTree(tooDense))).anySatisfy(
                error -> assertThat(error.getInstanceLocation().toString()).isEqualTo("/guideLine/particleSpacing"));
    }

    @Test
    void theSchemaAcceptsAGuideLineWithAndWithoutTheStyleFieldsAndRefusesAnUnknownParticle() {
        String styled = VALID_MAP.replace("\"particleSpacing\": 1.0,",
                "\"particleSpacing\": 1.0, \"mode\": \"FULL\", \"particle\": \"DUST\", \"dustColor\": \"#3366FF\",");
        String unknownParticle = VALID_MAP.replace("\"particleSpacing\": 1.0,",
                "\"particleSpacing\": 1.0, \"particle\": \"END-ROD\",");

        assertThat(errors(mapSchema(), JSON.readTree(VALID_MAP))).isEmpty();
        assertThat(errors(mapSchema(), JSON.readTree(styled))).isEmpty();
        assertThat(errors(mapSchema(), JSON.readTree(unknownParticle))).anySatisfy(
                error -> assertThat(error.getInstanceLocation().toString()).isEqualTo("/guideLine/particle"));
    }

    @Test
    void mapWithAMisspelledKeyIsRejectedNamingIt() {
        // The schema is stricter than the loader on purpose: the loader ignores unknown keys, the editor does not.
        String misspelt = VALID_MAP.replace("\"referenceTimeSeconds\"", "\"referenceTimeSecond\"");

        assertThat(errors(mapSchema(), JSON.readTree(misspelt))).anySatisfy(
                error -> assertThat(error.getMessage()).contains("referenceTimeSecond"));
    }

    private static void assertResolvesTo(String committed, String schemaFile) {
        Path file = SERVER_RESOURCES.resolve(committed);
        String reference = readCommitted(committed).get("$schema").asString();
        Path resolved = file.getParent().resolve(reference).normalize();

        assertThat(resolved.toAbsolutePath().normalize())
                .as("$schema of %s", committed)
                .isEqualTo(PLATFORM_SCHEMAS.resolve(schemaFile).toAbsolutePath().normalize());
        assertThat(resolved).exists();
    }

    private static JsonNode readCommitted(String relative) {
        try {
            return JSON.readTree(Files.readString(SERVER_RESOURCES.resolve(relative)));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static List<Error> errors(Schema schema, JsonNode document) {
        return schema.validate(document);
    }

    private static Schema mapSchema() {
        return schema("map.schema.json");
    }

    private static Schema cupSchema() {
        return schema("cup.schema.json");
    }

    private static Schema mapV1Schema() {
        return schema("map-v1.schema.json");
    }

    private static Schema schema(String name) {
        try (InputStream in = FileSchemaTest.class.getResourceAsStream("/schema/" + name)) {
            assertThat(in).as("classpath resource /schema/%s".formatted(name)).isNotNull();
            // Messages are pinned to English: the validator otherwise renders them in the JVM's default locale,
            // and a test that asserts on message text must not depend on the machine it runs on.
            SchemaRegistry registry = SchemaRegistry.builder()
                    .schemaRegistryConfig(SchemaRegistryConfig.builder().locale(Locale.ENGLISH).build())
                    .defaultDialectId(SpecificationVersion.DRAFT_2020_12.getDialectId())
                    .build();
            return registry.getSchema(in);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
