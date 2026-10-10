package net.elytrarace.voyager.setup.adapter;

import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.CustomData;
import org.jetbrains.annotations.ApiStatus;

/**
 * The setup wand: a blaze rod carrying a custom-data tag, which is how a click finds out it is a wand. A plain blaze
 * rod has no tag (research 006, spike 1.2).
 */
@ApiStatus.Internal
public abstract class Wand {

    private static final String TAG = "voyager";
    private static final String VALUE = "setup_wand";

    private Wand() {
    }

    public static ItemStack item() {
        CustomData tag = new CustomData(CompoundBinaryTag.builder().putString(TAG, VALUE).build());
        return ItemStack.of(Material.BLAZE_ROD).with(DataComponents.CUSTOM_DATA, tag);
    }

    public static boolean is(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && VALUE.equals(data.nbt().getString(TAG));
    }

    public static boolean isHeldBy(Player player) {
        for (ItemStack stack : player.getInventory().getItemStacks()) {
            if (is(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Gives the wand unless the player already holds one, and says whether it gave one. */
    public static boolean giveIfMissing(Player player) {
        if (isHeldBy(player)) {
            return false;
        }
        player.getInventory().addItemStack(item());
        return true;
    }
}
