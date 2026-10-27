package net.elytrarace.voyager.platform.lobby.items;

import net.kyori.adventure.text.TranslatableComponent;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.player.PlayerSwapItemEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The lobby's hotbar items: the leave door in slot 8, the tutorial book in slot 1, their removal at the start, and the
 * cancelled drop and swap. Each test builds its own instance and racer.
 */
@EnvTest
class LobbyItemsTest {

    @Test
    void aWaitingRacerReceivesTheDoorInSlotEightAndTheBookInSlotOne(Env env) {
        Instance lobby = env.createFlatInstance();
        Player racer = env.createPlayer(lobby, new Pos(0, 60, 0));

        LobbyItems.give(racer);

        assertThat(LobbyItems.kindOf(racer.getInventory().getItemStack(LobbyItems.LEAVE_SLOT)))
                .contains(LobbyItems.Kind.LEAVE);
        assertThat(LobbyItems.kindOf(racer.getInventory().getItemStack(LobbyItems.TUTORIAL_SLOT)))
                .contains(LobbyItems.Kind.TUTORIAL);
    }

    @Test
    void theItemsAreRemovedWhenTheRaceStarts(Env env) {
        Instance lobby = env.createFlatInstance();
        Player racer = env.createPlayer(lobby, new Pos(0, 60, 0));
        LobbyItems.give(racer);

        LobbyItems.remove(racer);

        assertThat(LobbyItems.kindOf(racer.getInventory().getItemStack(LobbyItems.LEAVE_SLOT))).isEmpty();
        assertThat(LobbyItems.kindOf(racer.getInventory().getItemStack(LobbyItems.TUTORIAL_SLOT))).isEmpty();
    }

    @Test
    void aDropOfTheDoorIsCancelled(Env env) {
        Instance lobby = env.createFlatInstance();
        Player racer = env.createPlayer(lobby, new Pos(0, 60, 0));
        ItemDropEvent drop = new ItemDropEvent(racer, LobbyItems.leaveItem());

        LobbyItems.keepDrop(drop);

        assertThat(drop.isCancelled()).as("the item stays in the racer's inventory and nothing is dropped").isTrue();
    }

    @Test
    void aSwapOfTheBookIntoTheOffHandIsCancelled(Env env) {
        Instance lobby = env.createFlatInstance();
        Player racer = env.createPlayer(lobby, new Pos(0, 60, 0));
        PlayerSwapItemEvent swap = new PlayerSwapItemEvent(racer, LobbyItems.tutorialItem(), ItemStack.AIR);

        LobbyItems.keepSwap(swap);

        assertThat(swap.isCancelled()).isTrue();
    }

    @Test
    void aDropOfAnotherItemIsLeftAlone(Env env) {
        Instance lobby = env.createFlatInstance();
        Player racer = env.createPlayer(lobby, new Pos(0, 60, 0));
        ItemDropEvent drop = new ItemDropEvent(racer, ItemStack.of(net.minestom.server.item.Material.STONE));

        LobbyItems.keepDrop(drop);

        assertThat(drop.isCancelled()).isFalse();
    }

    @Test
    void theItemNamesAreTranslationKeysInBothLanguages() {
        assertThat(((TranslatableComponent) LobbyItems.leaveItem().get(DataComponents.CUSTOM_NAME)).key())
                .isEqualTo("voyager.lobby.item.leave");
        assertThat(((TranslatableComponent) LobbyItems.tutorialItem().get(DataComponents.CUSTOM_NAME)).key())
                .isEqualTo("voyager.lobby.item.tutorial");
    }

    @Test
    void anOrdinaryItemIsNoLobbyItem() {
        assertThat(LobbyItems.kindOf(ItemStack.of(net.minestom.server.item.Material.STONE)))
                .isEqualTo(Optional.empty());
    }
}
