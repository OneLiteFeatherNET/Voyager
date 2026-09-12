package net.elytrarace.voyager.platform.catalog.adapter;

import com.google.gson.JsonParseException;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.exception.InvalidBoostConfigException;
import net.elytrarace.voyager.api.race.exception.InvalidMapException;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MapDefinitionAdapterTest {

    // The name and the world differ, so a reader that used one for the other cannot pass; the spawn
    // is nowhere near either ring centre; the reference time is fractional.
    private static final String MAP = """
            {
              "name": "elytraraceblueandred",
              "world": "ElytraraceBlueAndRed",
              "spawn": { "x": 109.0, "y": -62.0, "z": 54.0 },
              "referenceTimeSeconds": 46.7,
              "boostConfig": { "burnDurationTicks": 18, "cooldownTicks": 41 },
              "rings": [
                {
                  "index": 0,
                  "center": { "x": 85.0, "y": -54.0, "z": 54.0 },
                  "normal": { "x": -1.0, "y": 0.0, "z": 0.0 },
                  "radius": 3.605551275463989,
                  "points": 10,
                  "type": "STANDARD"
                },
                {
                  "index": 1,
                  "center": { "x": 2.0, "y": -31.0, "z": 69.0 },
                  "normal": { "x": 0.0, "y": 1.0, "z": 0.0 },
                  "radius": 3.0,
                  "points": 25,
                  "type": "BOOST"
                }
              ],
              "notes": [ "PROVISIONAL seed", "another line" ]
            }
            """;

    @Test
    void readsTheNameWorldSpawnAndReferenceTime() {
        MapDefinition map = Adapters.GSON.fromJson(MAP, MapDefinition.class);

        assertThat(map.name()).isEqualTo("elytraraceblueandred");
        assertThat(map.world()).isEqualTo("ElytraraceBlueAndRed");
        assertThat(map.spawn()).isEqualTo(new Vec3(109, -62, 54));
        // 46.7 s, not 46 and not 47: the value is a lap time a designer edits, and rounding it to
        // whole seconds would move every medal bracket on the map.
        assertThat(map.referenceTime()).isEqualTo(Duration.ofMillis(46_700));
    }

    /**
     * Both numbers, in ticks. 18 and 41 are distinct, not multiples of one another, and neither is
     * the Vanilla-derived default of 30 — so an adapter that read one field into the other, or that
     * fell back to a built-in, produces a value that is nowhere in this fixture.
     */
    @Test
    void readsTheBoostTuningAsTwoTickCounts() {
        MapDefinition map = Adapters.GSON.fromJson(MAP, MapDefinition.class);

        assertThat(map.boostConfig()).isEqualTo(new BoostConfig(18, 41));
    }

    @Test
    void refusesAMapMissingItsBoostTuning() {
        // Not defaulted, for the reason every other field here is not: an absent boost configuration
        // has no harmless value, and a map racing on tuning nobody chose is not findable in the data.
        assertThatThrownBy(() -> Adapters.GSON.fromJson(MAP.replaceAll("\\s*\"boostConfig\".*\n", "\n"),
                MapDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("missing the field 'boostConfig'");
    }

    @Test
    void refusesABoostConfigMissingOneOfItsTwoNumbers() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(
                MAP.replace("\"burnDurationTicks\": 18, ", ""), MapDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("missing the field 'burnDurationTicks'");
        assertThatThrownBy(() -> Adapters.GSON.fromJson(
                MAP.replace(", \"cooldownTicks\": 41", ""), MapDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("missing the field 'cooldownTicks'");
    }

    /**
     * The one invariant that is not about this file being well formed: a cooldown no longer than the
     * burn lets two rockets burn on one racer at once, which the simulation's boolean input cannot
     * express. {@code BoostConfig} refuses it and the refusal has to survive the read rather than
     * being swallowed into some default.
     */
    @Test
    void letsTheBoostConfigItselfRefuseACooldownThatDoesNotOutlastTheBurn() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(
                MAP.replace("\"cooldownTicks\": 41", "\"cooldownTicks\": 18"), MapDefinition.class))
                .isInstanceOf(InvalidBoostConfigException.class)
                .hasMessageContaining("cannot express");
    }

    @Test
    void readsTheRingsInFileOrder() {
        MapDefinition map = Adapters.GSON.fromJson(MAP, MapDefinition.class);

        assertThat(map.rings()).extracting(Ring::index).containsExactly(0, 1);
        assertThat(map.rings()).extracting(Ring::points).containsExactly(10, 25);
        assertThat(map.rings().get(0).center()).isEqualTo(new Vec3(85, -54, 54));
        assertThat(map.rings().get(1).center()).isEqualTo(new Vec3(2, -31, 69));
    }

    @Test
    void ignoresTheNotesTheConversionLeftForTheReader() {
        // The notes say which values were seeded rather than measured. They are for a person, and a
        // reader that choked on them would make an explanation an error.
        assertThat(Adapters.GSON.fromJson(MAP, MapDefinition.class).name()).isEqualTo("elytraraceblueandred");
        assertThat(Adapters.GSON.fromJson(MAP.replace("\"notes\"", "\"somethingElseEntirely\""),
                MapDefinition.class).name()).isEqualTo("elytraraceblueandred");
    }

    @Test
    void refusesAMapMissingItsWorld() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(MAP.replaceAll("\\s*\"world\".*\n", "\n"),
                MapDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("map 'elytraraceblueandred' is missing the field 'world'");
    }

    @Test
    void refusesAMapMissingItsSpawn() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(MAP.replaceAll("\\s*\"spawn\".*\n", "\n"),
                MapDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("missing the field 'spawn'");
    }

    @Test
    void refusesAMapMissingItsReferenceTime() {
        // Not defaulted. The reference time decides the medal tiers, and a map that silently used
        // somebody's idea of a sensible default would hand out the wrong medals quietly.
        assertThatThrownBy(() -> Adapters.GSON.fromJson(MAP.replaceAll("\\s*\"referenceTimeSeconds\".*\n", "\n"),
                MapDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("missing the field 'referenceTimeSeconds'");
    }

    @Test
    void letsTheMapItselfRefuseRingsThatAreNotInOrder() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(MAP.replace("\"index\": 1", "\"index\": 9"),
                MapDefinition.class))
                .isInstanceOf(InvalidMapException.class)
                .hasMessageContaining("0..n-1");
    }

    @Test
    void letsTheMapItselfRefuseAReferenceTimeOfZero() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(
                MAP.replace("\"referenceTimeSeconds\": 46.7", "\"referenceTimeSeconds\": 0"),
                MapDefinition.class))
                .isInstanceOf(InvalidMapException.class);
    }
}
