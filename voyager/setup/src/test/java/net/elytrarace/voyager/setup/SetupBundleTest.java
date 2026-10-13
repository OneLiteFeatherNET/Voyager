package net.elytrarace.voyager.setup;

import net.elytrarace.voyager.platform.text.Messages;
import net.elytrarace.voyager.platform.text.SetupMessages;
import net.elytrarace.voyager.platform.text.VoyagerTranslator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** The setup server's own bundle, read from the classpath exactly as the composition root reads it at boot. */
class SetupBundleTest {

    private static final VoyagerTranslator TRANSLATOR = VoyagerTranslator.fromClasspath(SetupMessages.BUNDLE);

    private static String render(Component component, Locale locale) {
        Component rendered = TRANSLATOR.translate((TranslatableComponent) component, locale);
        assertThat(rendered)
                .as("the setup bundle must translate this key, or the player sees the dotted key")
                .isNotNull();
        return PlainTextComponentSerializer.plainText().serialize(rendered);
    }

    @Test
    void anUnauthorisedSetupUserSeesTheEnglishRefusalNotTheKey() {
        assertThat(render(Messages.commandDenied(), Locale.US))
                .isEqualTo("You may not run this command");
    }
}
