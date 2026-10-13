package net.elytrarace.voyager.platform.hud;

import net.elytrarace.voyager.api.race.MedalTier;
import net.kyori.adventure.key.Key;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each meaning has its own sound. The countdown beat is for a countdown digit only, so a reset, a DNF and a missed
 * ring must not sound like one, and a loss must be quieter than a medal. These are asserted on the sounds the
 * feedback builds, not on anything played.
 */
class RaceFeedbackSoundTest {

    private static final Key COUNTDOWN_BEAT = Key.key("block.note_block.bass");

    @Test
    void aResetSoundIsNotTheCountdownBeat() {
        assertThat(RaceFeedback.resetSound().name())
                .as("reset sound")
                .isNotEqualTo(COUNTDOWN_BEAT);
    }

    @Test
    void aDnfSoundIsNotTheCountdownBeat() {
        assertThat(RaceFeedback.medalSound(MedalTier.DNF).name())
                .as("DNF sound")
                .isNotEqualTo(COUNTDOWN_BEAT);
    }

    @Test
    void aMissedRingSoundIsNotTheCountdownBeat() {
        assertThat(RaceFeedback.missedRingSound().name())
                .as("missed-ring sound")
                .isNotEqualTo(COUNTDOWN_BEAT);
    }

    @Test
    void aDnfIsQuieterThanEveryMedal() {
        float dnfVolume = RaceFeedback.medalSound(MedalTier.DNF).volume();

        for (MedalTier medal : List.of(MedalTier.DIAMOND, MedalTier.GOLD, MedalTier.SILVER, MedalTier.BRONZE)) {
            assertThat(dnfVolume)
                    .as("DNF volume against %s", medal)
                    .isLessThan(RaceFeedback.medalSound(medal).volume());
        }
    }

    /** The reset, DNF, missed-ring, countdown and ring sounds are five different keys, so no two meanings share one. */
    @Test
    void theFiveSoundsAreDistinctKeys() {
        List<Key> keys = List.of(
                RaceFeedback.resetSound().name(),
                RaceFeedback.medalSound(MedalTier.DNF).name(),
                RaceFeedback.missedRingSound().name(),
                COUNTDOWN_BEAT,
                RaceFeedback.ringSound(0, 35).name());

        assertThat(Set.copyOf(keys))
                .as("distinct sound keys")
                .hasSize(keys.size());
    }

    @Test
    void theMissedRingSoundIsQuieterThanARingPass() {
        assertThat(RaceFeedback.missedRingSound().volume())
                .isLessThan(RaceFeedback.ringSound(0, 35).volume());
    }
}
