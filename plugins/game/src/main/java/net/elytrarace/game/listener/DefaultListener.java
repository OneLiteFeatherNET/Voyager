package net.elytrarace.game.listener;

import net.elytrarace.api.phase.LinearPhaseSeries;
import net.elytrarace.api.phase.Phase;
import net.elytrarace.common.cup.model.CupDTO;
import net.elytrarace.common.cup.model.ResolvedCupDTO;
import net.elytrarace.common.listener.CancellableListener;
import net.elytrarace.game.ElytraRace;
import net.elytrarace.game.phase.EndPhase;
import net.elytrarace.game.phase.GamePhase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.server.ServerListPingEvent;

public class DefaultListener implements Listener, CancellableListener {

    private final ElytraRace plugin;

    public DefaultListener(ElytraRace plugin) {
        this.plugin = plugin;
    }

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

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        LinearPhaseSeries<Phase> elytraPhase = this.plugin.getElytraPhase();
        if (elytraPhase == null) return;
        var currentPhase = elytraPhase.getCurrentPhase();
        if (currentPhase == null) return;
        if (currentPhase.isRunning() && (currentPhase instanceof GamePhase || currentPhase instanceof EndPhase)){
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, Component.empty());
        }
    }

    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        var cup = this.plugin.getCurrentCup().filter(ResolvedCupDTO.class::isInstance).orElse(null);
        if (cup == null) return;
        event.motd(Component.translatable("cup.motd", MiniMessage.miniMessage().deserialize(cup.displayName())));
    }

}
