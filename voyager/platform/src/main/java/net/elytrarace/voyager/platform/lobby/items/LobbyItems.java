package net.elytrarace.voyager.platform.lobby.items;

import net.elytrarace.voyager.platform.text.Messages;
import net.kyori.adventure.text.Component;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.player.PlayerSwapItemEvent;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Optional;

/**
 * The two items a waiting racer holds: the leave door in slot 8 and the tutorial book in slot 1, as Cygnus's lobby hints
 * give them. They are given on the join and while the room waits, removed before the racer enters the first map, and they
 * cannot be dropped or swapped into the off hand. Nothing about them is saved.
 *
 * <p>An item is recognised by a tag it carries, so an ordinary item is never taken for one of these.
 */
@ApiStatus.Internal
@NotNullByDefault
public final class LobbyItems {

    /** The hotbar slot of the leave door, the last one (Cygnus's {@code LOBBY_SLOT}). */
    public static final int LEAVE_SLOT = 8;
    /** The hotbar slot of the tutorial book, next to the first (Cygnus's {@code TUTORIAL_SLOT}). */
    public static final int TUTORIAL_SLOT = 1;

    /** Which lobby item a stack is. */
    public enum Kind {
        /** The door that sends the racer to the hub. */
        LEAVE,
        /** The book that opens the tutorial. */
        TUTORIAL
    }

    private static final Tag<String> KIND = Tag.String("voyager-lobby-item");
    private static final String LEAVE_VALUE = "leave";
    private static final String TUTORIAL_VALUE = "tutorial";

    private LobbyItems() {
    }

    /** The leave door: a dark oak door named "Leave", with a lore line that says where it sends the racer. */
    @Contract(pure = true)
    public static ItemStack leaveItem() {
        return ItemStack.builder(Material.DARK_OAK_DOOR)
                .set(DataComponents.CUSTOM_NAME, Messages.leaveItemName())
                .set(DataComponents.LORE, java.util.List.of(Messages.leaveItemLore()))
                .build()
                .withTag(KIND, LEAVE_VALUE);
    }

    /** The tutorial book, named "Tutorial". */
    @Contract(pure = true)
    public static ItemStack tutorialItem() {
        Component name = Messages.tutorialItemName();
        return ItemStack.builder(Material.ENCHANTED_BOOK)
                .set(DataComponents.CUSTOM_NAME, name)
                .build()
                .withTag(KIND, TUTORIAL_VALUE);
    }

    /**
     * Which lobby item a stack is, if it is one.
     *
     * @param stack the stack to look at
     * @return the kind, or empty for any other stack
     */
    public static Optional<Kind> kindOf(ItemStack stack) {
        String value = stack.getTag(KIND);
        if (LEAVE_VALUE.equals(value)) {
            return Optional.of(Kind.LEAVE);
        }
        if (TUTORIAL_VALUE.equals(value)) {
            return Optional.of(Kind.TUTORIAL);
        }
        return Optional.empty();
    }

    /**
     * Puts the leave door and the tutorial book into the racer's hotbar, where they are missing. Anything the racer already
     * holds in those slots is replaced, and the other slots are left as they are.
     *
     * @param racer the waiting racer
     */
    public static void give(Player racer) {
        PlayerInventory inventory = racer.getInventory();
        if (kindOf(inventory.getItemStack(LEAVE_SLOT)).orElse(null) != Kind.LEAVE) {
            inventory.setItemStack(LEAVE_SLOT, leaveItem());
        }
        if (kindOf(inventory.getItemStack(TUTORIAL_SLOT)).orElse(null) != Kind.TUTORIAL) {
            inventory.setItemStack(TUTORIAL_SLOT, tutorialItem());
        }
    }

    /**
     * Takes the lobby items out of the racer's inventory, wherever they are. Other items are left alone.
     *
     * @param racer the racer whose race is starting
     */
    public static void remove(Player racer) {
        PlayerInventory inventory = racer.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (kindOf(inventory.getItemStack(slot)).isPresent()) {
                inventory.setItemStack(slot, ItemStack.AIR);
            }
        }
    }

    /**
     * Cancels a drop of a lobby item, so it stays in the racer's inventory. Any other drop is left alone.
     *
     * @param drop the drop the server is about to make
     */
    public static void keepDrop(ItemDropEvent drop) {
        if (kindOf(drop.getItemStack()).isPresent()) {
            drop.setCancelled(true);
        }
    }

    /**
     * Cancels a swap that moves a lobby item into the off hand. A swap of other items is left alone.
     *
     * @param swap the swap the server is about to make
     */
    public static void keepSwap(PlayerSwapItemEvent swap) {
        if (kindOf(swap.getMainHandItem()).isPresent() || kindOf(swap.getOffHandItem()).isPresent()) {
            swap.setCancelled(true);
        }
    }
}
