package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.exception.InvalidGuideLineException;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuideLineStyleTest {

    private static final GuidePoint EARLY = new GuidePoint(2_450, new Vec3(-59.5, 131.9, -82.4));

    @Test
    void aGuideLineWithNoStyleNamesModeFullParticleEndRodAndTheAmberColour() {
        GuideLine line = new GuideLine(List.of(EARLY), 2, 2.0);

        assertThat(line.mode()).isEqualTo(GuideLineMode.FULL);
        assertThat(line.particle()).isEqualTo(GuideParticle.END_ROD);
        assertThat(line.dustRgb()).isNull();
        assertThat(line.dustRgbOrDefault()).isEqualTo(0xFFC800);
    }

    @Test
    void anUnknownModeIsRefusedAndNamed() {
        assertThatThrownBy(() -> GuideLineMode.parse("SPIRAL"))
                .isInstanceOf(InvalidGuideLineException.class)
                .hasMessageContaining("SPIRAL");
    }

    @Test
    void anUnknownParticleIsRefusedAndNamed() {
        assertThatThrownBy(() -> GuideParticle.parse("FLAME"))
                .isInstanceOf(InvalidGuideLineException.class)
                .hasMessageContaining("FLAME");
    }

    @Test
    void aColourThatIsNotSixHexadecimalDigitsIsRefused() {
        assertThatThrownBy(() -> GuideLine.parseDustColor("blue"))
                .isInstanceOf(InvalidGuideLineException.class)
                .hasMessageContaining("blue");
        assertThatThrownBy(() -> GuideLine.parseDustColor("#FFF"))
                .isInstanceOf(InvalidGuideLineException.class);
    }

    @Test
    void aSixDigitColourParsesToItsRgbValue() {
        assertThat(GuideLine.parseDustColor("#3366FF")).isEqualTo(0x3366FF);
    }

    @Test
    void aColourOnEndRodIsRefusedAndNamesBothFields() {
        assertThatThrownBy(() -> new GuideLine(List.of(EARLY), 2, 2.0, GuideLineMode.FULL, GuideParticle.END_ROD,
                0x3366FF))
                .isInstanceOf(InvalidGuideLineException.class)
                .hasMessageContaining("dustColor")
                .hasMessageContaining("particle");
    }

    @Test
    void theThreeArgumentConstructorKeepsItsMeaning() {
        GuideLine line = new GuideLine(List.of(EARLY), 2, 2.0);

        assertThat(line.lookAheadRings()).isEqualTo(2);
        assertThat(line.particleSpacing()).isEqualTo(2.0);
        assertThat(line.points()).containsExactly(EARLY);
        assertThat(line).isEqualTo(new GuideLine(List.of(EARLY), 2, 2.0, GuideLineMode.FULL, GuideParticle.END_ROD, null));
    }
}
