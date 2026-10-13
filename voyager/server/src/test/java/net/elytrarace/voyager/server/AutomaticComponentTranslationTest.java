package net.elytrarace.voyager.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The game server switches on Minestom's automatic component translation itself, so it starts without
 * a {@code -Dminestom.automatic-component-translation=true} JVM argument.
 *
 * <p>The system property is saved and restored around each test, so no test sees another's value.
 */
class AutomaticComponentTranslationTest {

    private String previous;

    @BeforeEach
    void clearTheFlag() {
        previous = System.getProperty(VoyagerServer.AUTOMATIC_TRANSLATION_PROPERTY);
        System.clearProperty(VoyagerServer.AUTOMATIC_TRANSLATION_PROPERTY);
    }

    @AfterEach
    void restoreTheFlag() {
        if (previous == null) {
            System.clearProperty(VoyagerServer.AUTOMATIC_TRANSLATION_PROPERTY);
        } else {
            System.setProperty(VoyagerServer.AUTOMATIC_TRANSLATION_PROPERTY, previous);
        }
    }

    @Test
    void enablingTranslationSetsTheMinestomPropertyWhenTheJvmArgumentIsAbsent() {
        VoyagerServer.enableAutomaticComponentTranslation();

        assertThat(System.getProperty(VoyagerServer.AUTOMATIC_TRANSLATION_PROPERTY))
                .as("the property Minestom's ServerFlag reads on first load")
                .isEqualTo("true");
    }
}
