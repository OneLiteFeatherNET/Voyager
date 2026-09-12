package net.elytrarace.voyager.platform.hud;

import net.elytrarace.voyager.api.race.MedalTier;
import net.kyori.adventure.sound.Sound;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The choices behind every sound a racer hears, which is the channel this design leans on hardest:
 * it costs no screen space at 33 blocks a second and has no read latency.
 */
class RaceFeedbackTest {

    private static final int COMMITTED_COURSE_RINGS = 35;

    @Test
    void theRingLadderRunsFromOneToOnePointEightAcrossTheWholeCourse() {
        assertThat(RaceFeedback.pitchFor(0, COMMITTED_COURSE_RINGS)).isCloseTo(1.0f, within(1e-6f));
        assertThat(RaceFeedback.pitchFor(COMMITTED_COURSE_RINGS - 1, COMMITTED_COURSE_RINGS))
                .isCloseTo(1.8f, within(1e-6f));
    }

    /**
     * The ladder has to be strictly rising, ring by ring, or a racer hears a repeat rather than
     * progress. Asserted over every ring of the committed course rather than at its two ends, where
     * a formula that flattened in the middle would still pass.
     */
    @Test
    void everyRingIsHigherThanTheOneBeforeIt() {
        List<Float> pitches = new ArrayList<>();
        for (int ring = 0; ring < COMMITTED_COURSE_RINGS; ring++) {
            pitches.add(RaceFeedback.pitchFor(ring, COMMITTED_COURSE_RINGS));
        }

        assertThat(pitches).isSorted();
        assertThat(pitches).doesNotHaveDuplicates();
    }

    /** The protocol accepts 0.5 to 2.0, and the ladder stays inside it with headroom at the top. */
    @Test
    void theWholeLadderStaysInsideThePitchTheProtocolAccepts() {
        for (int rings : new int[] { 2, 12, 35, 120 }) {
            for (int ring = 0; ring < rings; ring++) {
                assertThat(RaceFeedback.pitchFor(ring, rings))
                        .as("ring %d of %d", ring, rings)
                        .isBetween(0.5f, 2.0f);
            }
        }
    }

    /**
     * A one-ring map has no span to climb. Dividing by it would give {@code NaN}, which Minestom
     * writes to the wire and the client rejects — a crash reached only by a course nobody flies but
     * every test fixture builds.
     */
    @Test
    void aSingleRingCourseRingsAtTheBottomOfTheLadderRatherThanAtNotANumber() {
        assertThat(RaceFeedback.pitchFor(0, 1)).isEqualTo(1.0f);
        assertThat(Float.isNaN(RaceFeedback.pitchFor(0, 1))).isFalse();
    }

    /**
     * Finishing is a different sound, not a higher bell. Thirty-five rising bells would make the
     * thirty-fifth the loudest note in a run rather than the end of one.
     */
    @Test
    void theLastRingSoundsCategoricallyUnlikeEveryRingBeforeIt() {
        Sound lastRing = RaceFeedback.ringSound(COMMITTED_COURSE_RINGS - 1, COMMITTED_COURSE_RINGS);
        Sound secondToLast = RaceFeedback.ringSound(COMMITTED_COURSE_RINGS - 2, COMMITTED_COURSE_RINGS);

        assertThat(lastRing.name()).isNotEqualTo(secondToLast.name());
        assertThat(lastRing.name().asString()).isEqualTo("minecraft:ui.toast.challenge_complete");
        assertThat(secondToLast.name().asString()).isEqualTo("minecraft:block.note_block.bell");
    }

    /**
     * {@code MASTER} for everything. A player who turned Blocks down to hear their own music must
     * not thereby have turned off the game's primary confirmation channel.
     */
    @Test
    void everySoundIsOnTheMasterChannel() {
        List<Sound> sounds = new ArrayList<>();
        sounds.add(RaceFeedback.ringSound(0, COMMITTED_COURSE_RINGS));
        sounds.add(RaceFeedback.ringSound(COMMITTED_COURSE_RINGS - 1, COMMITTED_COURSE_RINGS));
        for (MedalTier tier : MedalTier.values()) {
            sounds.add(RaceFeedback.medalSound(tier));
        }

        assertThat(sounds).allSatisfy(sound ->
                assertThat(sound.source()).isEqualTo(Sound.Source.MASTER));
    }

    /**
     * A DNF must not sound like a win, and a finish outside every bracket must not sound like a
     * medal. The three that are medals share a sound at three pitches; the two that are not have
     * their own.
     */
    @Test
    void theResultSoundIsProportionalToTheResult() {
        assertThat(RaceFeedback.medalSound(MedalTier.DIAMOND).name().asString())
                .isEqualTo("minecraft:ui.toast.challenge_complete");
        assertThat(RaceFeedback.medalSound(MedalTier.GOLD).name().asString())
                .isEqualTo("minecraft:entity.player.levelup");
        assertThat(RaceFeedback.medalSound(MedalTier.DNF).name())
                .isNotEqualTo(RaceFeedback.medalSound(MedalTier.DIAMOND).name());
        assertThat(RaceFeedback.medalSound(MedalTier.FINISH).name())
                .isNotEqualTo(RaceFeedback.medalSound(MedalTier.BRONZE).name());
    }

    @Test
    void theThreeMedalsThatShareASoundAreSeparatedByPitchAlone() {
        float gold = RaceFeedback.medalSound(MedalTier.GOLD).pitch();
        float silver = RaceFeedback.medalSound(MedalTier.SILVER).pitch();
        float bronze = RaceFeedback.medalSound(MedalTier.BRONZE).pitch();

        assertThat(gold).isGreaterThan(silver);
        assertThat(silver).isGreaterThan(bronze);
    }

    @Test
    void aDnfIsTheLowestNoteInTheGame() {
        float dnf = RaceFeedback.medalSound(MedalTier.DNF).pitch();

        for (MedalTier tier : MedalTier.values()) {
            if (tier != MedalTier.DNF) {
                assertThat(dnf)
                        .as("a loss must not be mistakable for %s", tier)
                        .isLessThan(RaceFeedback.medalSound(tier).pitch());
            }
        }
    }

    /** Three, two, one is heard as a run rather than as the same beat three times. */
    @Test
    void theCountdownBeatRisesWithEachDigit() {
        assertThat(RaceFeedback.countdownPitch(3)).isLessThan(RaceFeedback.countdownPitch(2));
        assertThat(RaceFeedback.countdownPitch(2)).isLessThan(RaceFeedback.countdownPitch(1));
    }
}
