package net.elytrarace.voyager.platform.text;

import net.elytrarace.voyager.api.race.MedalTier;
import net.elytrarace.voyager.race.scoring.MedalOutlook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The boss bar title as keys, arguments and colours: what the racer's line is built from.
 *
 * <p>The words themselves are the bundle's, and {@code voyager-server}'s {@code MessageBundleTest}
 * renders the shipped bundle in both locales and reads them back. What is decided here is which key
 * each part of the line uses, which number it is given, and which colour the tier phrase carries.
 */
class MessagesBossBarTest {

    private static TranslatableComponent translatable(Component component) {
        assertThat(component).isInstanceOf(TranslatableComponent.class);
        return (TranslatableComponent) component;
    }

    private static Component argument(TranslatableComponent component, int index) {
        return (Component) component.arguments().get(index).value();
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /**
     * The tier and the time left are one phrase, coloured with the tier's own colour, so a racer sees
     * which band they are in and how long it lasts in the same colour.
     */
    @Test
    void theTierPhraseNamesTheBandAndTheTimeLeftInTheBandsColour() {
        MedalOutlook diamond = new MedalOutlook(MedalTier.DIAMOND, Optional.of(Duration.ofMillis(57_900)));

        TranslatableComponent bar = translatable(Messages.bossBarTitle(1, 2, "Blue & Red", diamond));

        assertThat(bar.key()).isEqualTo("voyager.hud.bossbar");
        TranslatableComponent phrase = translatable(argument(bar, 3));
        assertThat(phrase.key()).isEqualTo("voyager.hud.medal.left");
        assertThat(phrase.color()).isEqualTo(Palette.DIAMOND);
        assertThat(translatable(argument(phrase, 0)).key()).isEqualTo("voyager.hud.tier.diamond");
    }

    @Test
    void theTimeLeftIsSecondsToOneDecimalWithADot() {
        MedalOutlook diamond = new MedalOutlook(MedalTier.DIAMOND, Optional.of(Duration.ofMillis(57_900)));

        TranslatableComponent phrase = translatable(argument(
                translatable(Messages.bossBarTitle(1, 2, "Blue & Red", diamond)), 3));

        assertThat(plain(argument(phrase, 1))).isEqualTo("57.9");
    }

    /** The finish band has no countdown, so its phrase is the tier name alone, in the dim colour. */
    @Test
    void theFinishBandNamesItsTierAloneAndHasNoCountdown() {
        MedalOutlook finish = new MedalOutlook(MedalTier.FINISH, Optional.empty());

        TranslatableComponent bar = translatable(Messages.bossBarTitle(2, 2, "Blue & Red", finish));

        assertThat(bar.key()).isEqualTo("voyager.hud.bossbar.last");
        TranslatableComponent phrase = translatable(argument(bar, 3));
        assertThat(phrase.key()).isEqualTo("voyager.hud.medal.last");
        assertThat(phrase.color()).isEqualTo(Palette.FINISH);
        assertThat(translatable(argument(phrase, 0)).key()).isEqualTo("voyager.hud.tier.finish");
    }

    /** The map is named by the name it is given, and its position in the cup is passed as a number, not a name. */
    @Test
    void theMapPositionAndNameArePassedThroughUnchanged() {
        MedalOutlook diamond = new MedalOutlook(MedalTier.DIAMOND, Optional.of(Duration.ofSeconds(30)));

        TranslatableComponent bar = translatable(Messages.bossBarTitle(1, 2, "Blue & Red", diamond));

        assertThat(plain(argument(bar, 0))).isEqualTo("1");
        assertThat(plain(argument(bar, 1))).isEqualTo("2");
        assertThat(plain(argument(bar, 2))).isEqualTo("Blue & Red");
    }
}
