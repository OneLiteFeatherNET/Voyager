package net.elytrarace.voyager.platform.lobby.sound;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.key.Key;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The lobby's sound table: the pling at the marked seconds with Cygnus's volume and pitch, and the two one-off sounds. Pure
 * values, so nothing here needs a server or a clock.
 */
class LobbyCountdownSoundsTest {

    @Test
    void thePlingPlaysAtTenFiveThreeTwoOneAndZeroSecondsWithTheTableVolumeAndPitch() {
        assertThat(LobbyCountdownSounds.plingAt(10)).contains(new LobbyCountdownSounds.Pling(10, 0.5f, 1.000f));
        assertThat(LobbyCountdownSounds.plingAt(5)).contains(new LobbyCountdownSounds.Pling(5, 0.6f, 0.944f));
        assertThat(LobbyCountdownSounds.plingAt(3)).contains(new LobbyCountdownSounds.Pling(3, 0.7f, 0.667f));
        assertThat(LobbyCountdownSounds.plingAt(2)).contains(new LobbyCountdownSounds.Pling(2, 0.75f, 0.630f));
        assertThat(LobbyCountdownSounds.plingAt(1)).contains(new LobbyCountdownSounds.Pling(1, 0.85f, 0.561f));
        assertThat(LobbyCountdownSounds.plingAt(0)).contains(new LobbyCountdownSounds.Pling(0, 0.9f, 0.500f));
    }

    @Test
    void noPlingPlaysBetweenTheMarks() {
        assertThat(LobbyCountdownSounds.plingAt(7))
                .as("seven seconds left is not a marked second, so no pling plays")
                .isEmpty();
        assertThat(LobbyCountdownSounds.plingAt(4)).isEqualTo(Optional.empty());
    }

    @Test
    void thePlingIsTheNoteBlockPlingOnTheMasterSource() {
        Sound sound = LobbyCountdownSounds.pling(new LobbyCountdownSounds.Pling(10, 0.5f, 1.0f));

        assertThat(sound.name()).isEqualTo(Key.key("block.note_block.pling"));
        assertThat(sound.source()).isEqualTo(Sound.Source.MASTER);
    }

    @Test
    void theJoinSoundIsTheSoftPickupAtVolumeFourTenths() {
        Sound sound = LobbyCountdownSounds.joinSound();

        assertThat(sound.name()).isEqualTo(Key.key("entity.item.pickup"));
        assertThat(sound.volume()).isEqualTo(0.4f);
        assertThat(sound.pitch()).isEqualTo(1.0f);
    }

    @Test
    void theFullLobbySoundIsTheNoteBlockChime() {
        Sound sound = LobbyCountdownSounds.fullLobbySound();

        assertThat(sound.name())
                .as("the owner chose the Cygnus chime for a full lobby, not the xylophone")
                .isEqualTo(Key.key("block.note_block.chime"));
    }

    @Test
    void theFullLobbySoundIsPlayedOnceAtVolumeEightTenths() {
        Sound sound = LobbyCountdownSounds.fullLobbySound();

        assertThat(sound.volume()).isEqualTo(0.8f);
        assertThat(sound.pitch()).isEqualTo(1.0f);
    }
}
