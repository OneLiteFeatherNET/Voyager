package net.elytrarace.game.listener;

import net.elytrarace.api.database.model.DatabaseElytraPlayer;
import net.elytrarace.api.database.service.DatabaseService;
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
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.block.data.type.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.server.ServerListPingEvent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class DefaultListener implements Listener, CancellableListener {

    private static final List<Class<?>> RIGHT_CLICK_DENIED_INTERACTABLES = List.of(Sign.class, Chest.class, Container.class);
    private static final List<Class<?>> PHYSICS_DENIED_INTERACTABLES = List.of(Farmland.class);

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
        DatabaseService databaseService = this.plugin.getDatabaseService();
        if (databaseService == null) return;
        databaseService.getElytraPlayerRepository()
                .ifPresent(elytraPlayerRepository -> elytraPlayerRepository
                        .getElytraPlayerById(event.getPlayer().getUniqueId())
                        .thenApplyAsync(databaseElytraPlayer -> Objects
                                .requireNonNullElseGet(databaseElytraPlayer, () -> new DatabaseElytraPlayer(event.getPlayer().getUniqueId()))
                        )
                        .thenAcceptAsync(elytraPlayerRepository::saveElytraPlayer));
    }

    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        var cup = this.plugin.getCurrentCup().filter(ResolvedCupDTO.class::isInstance).orElse(null);
        if (cup == null) return;
        event.motd(Component.translatable("cup.motd", MiniMessage.miniMessage().deserialize(cup.displayName())));
    }

    @EventHandler
    public void playerInteract(PlayerInteractEvent event) {
        Optional.ofNullable(event.getClickedBlock())
                .map(Block::getBlockData).ifPresent(blockData -> {
                    if (event.getAction().equals(Action.PHYSICAL) && PHYSICS_DENIED_INTERACTABLES.stream().anyMatch(clazz -> clazz.isInstance(blockData))) {
                        cancelEvent(event);
                        return;
                    }
                    if (event.getAction().equals(Action.RIGHT_CLICK_BLOCK) && RIGHT_CLICK_DENIED_INTERACTABLES.stream().anyMatch(clazz -> clazz.isInstance(blockData))) {
                        cancelEvent(event);
                        return;
                    }

                });
    }

}
