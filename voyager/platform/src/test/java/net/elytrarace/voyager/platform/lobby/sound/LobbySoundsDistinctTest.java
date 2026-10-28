package net.elytrarace.voyager.platform.lobby.sound;

import net.elytrarace.voyager.platform.hud.RaceFeedback;
import net.kyori.adventure.key.Key;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No lobby sound may be a race sound. The race sample names come from {@link RaceFeedback}, so a change to one of them that
 * makes a lobby sound the same sample fails here.
 */
class LobbySoundsDistinctTest {

    @Test
    void noLobbySoundIsARaceSample() {
        assertThat(RaceFeedback.sampleKeysLobbyMustNotShare())
                .as("the race's ring bell, last-ring chime, reset, DNF, missed-ring and countdown beat samples")
                .doesNotContain(LobbyCountdownSounds.pling(new LobbyCountdownSounds.Pling(10, 0.5f, 1.0f)).name())
                .doesNotContain(LobbyCountdownSounds.joinSound().name())
                .doesNotContain(LobbyCountdownSounds.fullLobbySound().name());
    }

    /**
     * The full-lobby chime is the one lobby sound allowed to share a race sample: it is the reset chime, by the owner's choice
     * (race/lobby-feedback). Every other lobby sound is checked by {@link #noLobbySoundIsARaceSample()}.
     */
    @Test
    void theFullLobbyChimeIsTheOnlyLobbySoundThatSharesARaceSample() {
        assertThat(RaceFeedback.sampleKeysLobbyMustNotShare())
                .as("the full-lobby chime is the reset chime, which is one of the race samples")
                .contains(LobbyCountdownSounds.fullLobbySound().name());
        assertThat(LobbyCountdownSounds.pling(new LobbyCountdownSounds.Pling(10, 0.5f, 1.0f)).name())
                .as("the pling shares no race sample")
                .isNotIn(RaceFeedback.sampleKeysLobbyMustNotShare());
        assertThat(LobbyCountdownSounds.joinSound().name())
                .as("the join sound shares no race sample")
                .isNotIn(RaceFeedback.sampleKeysLobbyMustNotShare());
    }

    @Test
    void theLobbyPlingIsNotTheRaceCountdownBeat() {
        assertThat(RaceFeedback.sampleKeysLobbyMustNotShare())
                .as("the race's countdown beat is one of the samples a lobby sound must not share")
                .contains(Key.key("block.note_block.bass"));
        assertThat(LobbyCountdownSounds.pling(new LobbyCountdownSounds.Pling(10, 0.5f, 1.0f)).name())
                .as("the pling marks the lobby's countdown and is not the race's countdown beat")
                .isNotEqualTo(Key.key("block.note_block.bass"));
    }
}
