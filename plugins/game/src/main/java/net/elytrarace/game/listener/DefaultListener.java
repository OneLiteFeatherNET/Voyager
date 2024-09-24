package net.elytrarace.game.listener;

import net.elytrarace.common.listener.CancellableListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;

public class DefaultListener implements Listener, CancellableListener {

    @EventHandler
    public void onArmorStandManipulateEvent(PlayerArmorStandManipulateEvent event) {
        cancelEvent(event);
    }

    @EventHandler
    public void onLeafDecay(LeavesDecayEvent event) {
        cancelEvent(event);
    }

    @EventHandler
    public void onBedEnterEvent(PlayerBedEnterEvent event) {
        cancelEvent(event);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        cancelEvent(event);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        cancelEvent(event);
    }

    @EventHandler
    public void onHandleDrop(PlayerDropItemEvent event) {
        cancelEvent(event);
    }

    @EventHandler
    public void onHandlePickup(EntityPickupItemEvent event) {
        cancelEvent(event);
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        cancelEvent(event);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        cancelEvent(event);
    }

}
