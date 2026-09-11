package net.elytrarace.tools.converter;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.GameMode;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConverterOptionsTest {

    private static final List<String> MINIMUM = List.of("--source", "in", "--out", "out");

    @Test
    void readsEveryOptionAndKeepsThemApart() {
        ConverterOptions options = ConverterOptions.parse(List.of(
                "--source", "run/run/data",
                "--out", "voyager-server/src/main/resources",
                "--spawn", "ElytraraceBlueAndRed=109,-62,54",
                "--spawn", "nether-sprint=-8,71,3",
                "--reference-time-seconds", "46.7",
                "--points", "25",
                "--mode", "PRACTICE"));

        assertThat(options.source()).isEqualTo(Path.of("run/run/data"));
        assertThat(options.out()).isEqualTo(Path.of("voyager-server/src/main/resources"));
        assertThat(options.spawnFor("ElytraraceBlueAndRed")).isEqualTo(new Vec3(109, -62, 54));
        // Negative coordinates in a second, differently named map: a parser that kept one spawn for
        // all maps, or dropped the sign, cannot pass both of these.
        assertThat(options.spawnFor("nether-sprint")).isEqualTo(new Vec3(-8, 71, 3));
        assertThat(options.referenceTime()).isEqualTo(Duration.ofMillis(46_700));
        assertThat(options.points()).isEqualTo(25);
        assertThat(options.mode()).isEqualTo(GameMode.PRACTICE);
    }

    @Test
    void seedsTheTwoValuesTheOldDataDoesNotCarry() {
        ConverterOptions options = ConverterOptions.parse(MINIMUM);

        assertThat(options.referenceTime()).isEqualTo(Duration.ofSeconds(60));
        assertThat(options.points()).isEqualTo(10);
        assertThat(options.mode()).isEqualTo(GameMode.RACE);
    }

    @Test
    void refusesAMapWithNoSpawnRatherThanInventingOne() {
        ConverterOptions options = ConverterOptions.parse(MINIMUM);

        assertThatThrownBy(() -> options.spawnFor("ElytraraceBlueAndRed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("level.dat");
    }

    @Test
    void refusesTwoSpawnsForTheSameMap() {
        assertThatThrownBy(() -> ConverterOptions.parse(List.of(
                "--source", "in", "--out", "out", "--spawn", "a=1,2,3", "--spawn", "a=4,5,6")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("given twice");
    }

    @Test
    void refusesASpawnThatIsNotThreeNumbers() {
        assertThatThrownBy(() -> ConverterOptions.parse(List.of(
                "--source", "in", "--out", "out", "--spawn", "a=1,2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("three comma-separated coordinates");
    }

    @Test
    void refusesASpawnWithoutAMapName() {
        assertThatThrownBy(() -> ConverterOptions.parse(List.of(
                "--source", "in", "--out", "out", "--spawn", "=1,2,3")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("<world-dir>=<x>,<y>,<z>");
    }

    @Test
    void refusesANonPositiveReferenceTime() {
        assertThatThrownBy(() -> ConverterOptions.parse(List.of(
                "--source", "in", "--out", "out", "--reference-time-seconds", "0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be positive");
    }

    @Test
    void refusesAMissingSourceOrOut() {
        assertThatThrownBy(() -> ConverterOptions.parse(List.of("--source", "in")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--source and --out are both required");
    }

    @Test
    void refusesAnUnknownOption() {
        assertThatThrownBy(() -> ConverterOptions.parse(List.of("--source", "in", "--out", "out", "--fast", "yes")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown option '--fast'");
    }

    @Test
    void refusesAnOptionWithNoValue() {
        assertThatThrownBy(() -> ConverterOptions.parse(List.of("--source", "in", "--out")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--out needs a value");
    }
}
