package net.elytrarace.voyager.platform.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The setup wand instruction: two short lines, the placing one first, so that neither is a wall of text. */
class SetupMessagesTest {

    @Test
    void theWandInstructionIsTwoLinesPlacementFirst() {
        List<Component> lines = SetupMessages.wandInstructions();

        assertThat(lines).hasSize(2);
        assertThat(keyOf(lines.get(0))).isEqualTo("voyager.setup.wand.given.place");
        assertThat(keyOf(lines.get(1))).isEqualTo("voyager.setup.wand.given.remove");
    }

    private static String keyOf(Component component) {
        assertThat(component).isInstanceOf(TranslatableComponent.class);
        return ((TranslatableComponent) component).key();
    }
}
