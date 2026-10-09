package net.elytrarace.voyager.setup.spike;

import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.CustomData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spike 1.2 (identity part): can an item tag identify the wand. The tag is custom data on the stack,
 * so it survives the stack being moved and is absent from a plain item of the same material.
 */
class WandIdentitySpikeTest {

    @Test
    void aCustomDataTagIdentifiesTheWandAndNotAPlainItemOfTheSameMaterial() {
        CustomData tag = new CustomData(CompoundBinaryTag.builder().putString("voyager", "setup_wand").build());
        ItemStack wand = ItemStack.of(Material.BLAZE_ROD).with(DataComponents.CUSTOM_DATA, tag);
        ItemStack plain = ItemStack.of(Material.BLAZE_ROD);

        assertThat(wand.get(DataComponents.CUSTOM_DATA).nbt().getString("voyager")).isEqualTo("setup_wand");
        assertThat(plain.get(DataComponents.CUSTOM_DATA)).isNull();
        assertThat(wand.material()).isEqualTo(plain.material());
    }
}
