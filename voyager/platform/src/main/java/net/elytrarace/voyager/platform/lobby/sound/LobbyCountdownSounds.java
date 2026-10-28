package net.elytrarace.voyager.platform.lobby.sound;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Optional;

/**
 * The lobby's sounds and the seconds they mark.
 *
 * <p>The pling table is Cygnus's {@code LobbyCountdownSoundTask}, unchanged: the note-block pling at ten, five, three, two,
 * one and zero seconds left, each with its own volume and pitch. The join sound and the full-lobby sound were chosen in the
 * design of {@code cygnus-style-lobby}. The full-lobby sound is the Cygnus chime, as the owner chose in
 * {@code refine-lobby-countdown-feedback}. It is the race's reset sound too, the one lobby sound allowed to share a race
 * sample: the two are heard in different phases, and no other lobby sound may share one.
 */
@ApiStatus.Internal
@NotNullByDefault
public final class LobbyCountdownSounds {

    /**
     * One mark of the countdown: the seconds left at which it plays, and its volume and pitch.
     *
     * @param seconds the whole seconds left
     * @param volume  the volume, from zero to one
     * @param pitch   the pitch
     */
    public record Pling(long seconds, float volume, float pitch) {
    }

    private static final Key PLING = Key.key("block.note_block.pling");
    private static final Key JOIN = Key.key("entity.item.pickup");
    private static final Key FULL_LOBBY = Key.key("block.note_block.chime");
    private static final Sound.Source SOURCE = Sound.Source.MASTER;

    /** The marked seconds, from the Cygnus table, in the order they fall. */
    private static final Pling[] TABLE = {
            new Pling(10, 0.5f, 1.000f),
            new Pling(5, 0.6f, 0.944f),
            new Pling(3, 0.7f, 0.667f),
            new Pling(2, 0.75f, 0.630f),
            new Pling(1, 0.85f, 0.561f),
            new Pling(0, 0.9f, 0.500f),
    };

    private LobbyCountdownSounds() {
    }

    /**
     * The pling for a whole second of the countdown, if that second is marked.
     *
     * @param seconds the whole seconds left
     * @return the mark, or empty when the second is not one of the marked ones
     */
    @Contract(pure = true)
    public static Optional<Pling> plingAt(long seconds) {
        for (Pling mark : TABLE) {
            if (mark.seconds() == seconds) {
                return Optional.of(mark);
            }
        }
        return Optional.empty();
    }

    /**
     * The sound a mark plays.
     *
     * @param mark the mark from {@link #plingAt}
     */
    @Contract(pure = true)
    public static Sound pling(Pling mark) {
        return Sound.sound(PLING, SOURCE, mark.volume(), mark.pitch());
    }

    /** The soft sound a racer already waiting hears when another racer joins. */
    @Contract(pure = true)
    public static Sound joinSound() {
        return Sound.sound(JOIN, SOURCE, 0.4f, 1.0f);
    }

    /** The sound once when a full lobby shortens the countdown. */
    @Contract(pure = true)
    public static Sound fullLobbySound() {
        return Sound.sound(FULL_LOBBY, SOURCE, 0.8f, 1.0f);
    }
}
