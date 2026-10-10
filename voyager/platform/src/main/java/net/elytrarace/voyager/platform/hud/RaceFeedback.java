package net.elytrarace.voyager.platform.hud;

import net.elytrarace.voyager.api.race.MedalTier;
import net.elytrarace.voyager.platform.text.Messages;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.minestom.server.entity.Player;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

import java.time.Duration;

/**
 * Everything discrete a racer hears or is shown for a moment: a ring, the countdown, a result.
 *
 * <h2>The only file that names a sound or a title</h2>
 *
 * <p>When the game feels wrong, this is the file to open. Nothing else in the rebuild chooses a
 * sound, a pitch or a title duration, which is what makes "the confirmation is late" or "the medal
 * sting is too quiet" a one-file change rather than a search.
 *
 * <h2>In the tick that detected it, never scheduled</h2>
 *
 * <p>A ring pass is acknowledged in the same tick the pass was found. One tick is 50 ms, comfortably
 * inside the ~80 ms at which a confirmation stops reading as "that ring" and starts reading as
 * "something happened". Any later refactor that batches this onto a scheduler breaks the one property
 * the whole surface has.
 *
 * <h2>Sound first, colour third</h2>
 *
 * <p>Sound is the only channel with no screen cost at 33 blocks a second and no read latency, so it
 * carries the meaning. A ring's bell climbs in pitch with the ring number, which makes a racer
 * <em>hear</em> their own progress accumulate — thirty-five identical bells stop being information by
 * ring six. When the missed-ring signal lands, it will be told apart from this one by being a falling
 * double thud rather than a rising bell, and only then by symbol and colour: red and green is the
 * worst possible pair for deuteranopia, and here it is not even available, because the course spent
 * red on its own finish line.
 */
@ApiStatus.Internal
public abstract class RaceFeedback {

    /** Vanilla's tick, for expressing title timings in the unit the design reasons in. */
    private static final Duration TICK = Duration.ofMillis(50);

    private static final Key RING_BELL = Key.key("block.note_block.bell");
    private static final Key COUNTDOWN_BEAT = Key.key("block.note_block.bass");
    private static final Key LAUNCH = Key.key("block.note_block.pling");
    private static final Key CHALLENGE_COMPLETE = Key.key("ui.toast.challenge_complete");
    private static final Key LEVEL_UP = Key.key("entity.player.levelup");

    /**
     * The bottom of the ring ladder and how far it climbs.
     *
     * <p>Ring one rings at 1.0 and the last ring at 1.8, staying well inside the protocol's 0.5–2.0
     * with headroom at the top. On the committed 35-ring course that is a rising run of thirty-five
     * notes over a minute — the closest thing to a combo system that can be shipped without adding a
     * mechanic.
     */
    private static final float BASE_PITCH = 1.0f;

    private static final float PITCH_CLIMB = 0.8f;

    /**
     * {@code MASTER} and not {@code BLOCKS} or {@code PLAYERS}, for all of it.
     *
     * <p>This is race feedback, not world audio. A player who turned Blocks down to hear their own
     * music must not thereby have turned off the game's primary confirmation channel.
     */
    private static final Sound.Source SOURCE = Sound.Source.MASTER;

    private RaceFeedback() {
    }

    /**
     * A ring was passed: the bell, one step up the ladder.
     *
     * <p>The last ring is not a higher bell but a different sound, so finishing is categorically
     * unlike progressing rather than merely the top of the run.
     */
    public static void ringPassed(Player racer, int ringIndex, int ringCount) {
        racer.playSound(ringSound(ringIndex, ringCount));
    }

    /**
     * The sound a ring pass makes.
     *
     * @param ringIndex the zero-based index of the ring passed
     * @param ringCount how many rings the map has
     */
    @Contract(pure = true)
    public static Sound ringSound(int ringIndex, int ringCount) {
        if (ringIndex == ringCount - 1) {
            return Sound.sound(CHALLENGE_COMPLETE, SOURCE, 0.8f, 1.0f);
        }
        return Sound.sound(RING_BELL, SOURCE, 0.7f, pitchFor(ringIndex, ringCount));
    }

    /**
     * Where on the ladder a ring sits.
     *
     * <p>A one-ring map has no ladder to climb, and dividing by its zero-length span would be a
     * pitch of {@code NaN} — which Minestom writes to the wire and the client rejects. It rings at
     * the bottom of the ladder instead.
     */
    @Contract(pure = true)
    public static float pitchFor(int ringIndex, int ringCount) {
        if (ringCount <= 1) {
            return BASE_PITCH;
        }
        return BASE_PITCH + PITCH_CLIMB * ringIndex / (ringCount - 1);
    }

    /**
     * One digit of the start countdown, with the course under it.
     *
     * <p>No fade-in on any of them: a digit that fades in is a digit that arrives late, and a
     * countdown that arrives late is worse than no countdown. Stay fifteen ticks plus five out is
     * exactly one second, so each digit is gone before the next one arrives and the screen is clear
     * at the instant of the launch — which is the instant the racer needs to see the first ring.
     */
    public static void countdown(Player racer, int digit, Component subtitle) {
        racer.showTitle(Title.title(
                Messages.countdownDigit(digit),
                subtitle,
                Title.Times.times(Duration.ZERO, TICK.multipliedBy(15), TICK.multipliedBy(5))));
        racer.playSound(Sound.sound(COUNTDOWN_BEAT, SOURCE, 0.8f, countdownPitch(digit)));
    }

    /** The beat under each digit rises, so three of them are heard as a run and not as a repeat. */
    @Contract(pure = true)
    public static float countdownPitch(int digit) {
        return switch (digit) {
            case 3 -> 1.0f;
            case 2 -> 1.2f;
            default -> 1.4f;
        };
    }

    /** The launch. */
    public static void go(Player racer) {
        racer.showTitle(Title.title(
                Messages.go(),
                Component.empty(),
                Title.Times.times(Duration.ZERO, TICK.multipliedBy(10), TICK.multipliedBy(5))));
        racer.playSound(Sound.sound(LAUNCH, SOURCE, 0.9f, 2.0f));
    }

    /**
     * The map's result: one word and one line, held for three seconds.
     *
     * <p>The racer has landed and is allowed to read now, which is why the title region — off limits
     * for the whole race, because it is where the next ring appears — is finally usable.
     */
    public static void mapResult(Player racer, MedalTier medal, Component subtitle) {
        racer.showTitle(Title.title(
                Messages.medal(medal),
                subtitle,
                Title.Times.times(TICK.multipliedBy(5), TICK.multipliedBy(60), TICK.multipliedBy(10))));
        racer.playSound(medalSound(medal));
    }

    /**
     * The sound a result makes, proportional to what it is.
     *
     * <p>A DNF must not sound like a win, and a finish outside every bracket must not sound like a
     * medal — so the three that are medals share one sound at three pitches, and the two that are not
     * have their own.
     */
    @Contract(pure = true)
    public static Sound medalSound(MedalTier medal) {
        return switch (medal) {
            case DIAMOND -> Sound.sound(CHALLENGE_COMPLETE, SOURCE, 1.0f, 1.0f);
            case GOLD -> Sound.sound(LEVEL_UP, SOURCE, 0.9f, 1.2f);
            case SILVER -> Sound.sound(LEVEL_UP, SOURCE, 0.9f, 1.0f);
            case BRONZE -> Sound.sound(LEVEL_UP, SOURCE, 0.9f, 0.8f);
            case FINISH -> Sound.sound(LAUNCH, SOURCE, 0.9f, 1.0f);
            case DNF -> Sound.sound(COUNTDOWN_BEAT, SOURCE, 0.9f, 0.5f);
        };
    }

    /**
     * The sound a course reset makes: one low note on the countdown's beat, at the bottom of the register.
     *
     * <p>It is the opposite of the rising ring bell, so a racer who has just been sent back hears that they
     * were, without reading the title.
     */
    @Contract(pure = true)
    public static Sound resetSound() {
        return Sound.sound(COUNTDOWN_BEAT, SOURCE, 0.8f, 0.6f);
    }

    /**
     * A racer has been sent back by a reset: the title that names the reason and the target, then the reset
     * sound, in the tick the reset was detected. The title has no fade-in, as every title here does.
     */
    public static void reset(Player racer, Component title, Component subtitle) {
        racer.showTitle(Title.title(
                title,
                subtitle,
                Title.Times.times(Duration.ZERO, TICK.multipliedBy(40), TICK.multipliedBy(10))));
        racer.playSound(resetSound());
    }

    /** The cup's result: the longest title in the game, because it is the biggest moment in a session. */
    public static void cupResult(Player racer, Component subtitle) {
        racer.showTitle(Title.title(
                Messages.cupTitle(),
                subtitle,
                Title.Times.times(TICK.multipliedBy(10), TICK.multipliedBy(80), TICK.multipliedBy(20))));
        racer.playSound(Sound.sound(CHALLENGE_COMPLETE, SOURCE, 1.0f, 1.0f));
    }
}
