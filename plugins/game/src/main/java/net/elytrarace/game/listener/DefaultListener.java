package net.elytrarace.game.listener;

import net.elytrarace.api.database.model.DatabaseElytraPlayer;
import net.elytrarace.api.database.service.DatabaseService;
import net.elytrarace.api.phase.LinearPhaseSeries;
import net.elytrarace.api.phase.Phase;
import net.elytrarace.common.cup.model.ResolvedCupDTO;
import net.elytrarace.common.listener.CancellableListener;
import net.elytrarace.game.phase.EndPhase;
import net.elytrarace.game.phase.GamePhase;
import net.elytrarace.game.phase.LobbyPhase;
import net.elytrarace.game.phase.PreparationPhase;
import net.elytrarace.game.service.GameService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import java.util.Optional;

public class DefaultListener implements Listener, CancellableListener {

    private static final List<Class<?>> RIGHT_CLICK_DENIED_INTERACTABLES = List.of(Sign.class, Chest.class, Container.class);
    private static final List<Class<?>> PHYSICS_DENIED_INTERACTABLES = List.of(Farmland.class);

    private final GameService gameService;


    public DefaultListener(GameService gameService) {
        this.gameService = gameService;
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
    public void onPlayerJoin(PlayerJoinEvent event) {
        LinearPhaseSeries<Phase> elytraPhase = this.gameService.getElytraPhase();
        if (elytraPhase == null) return;
        var currentPhase = elytraPhase.getCurrentPhase();
        if (currentPhase == null) return;
        if (currentPhase.isRunning() && currentPhase instanceof LobbyPhase lobbyPhase) {
            event.getPlayer().teleportAsync(lobbyPhase.getLobbyLocation());
            event.joinMessage(Component.translatable("phase.lobby.player.join", Component.translatable("plugin.prefix"), event.getPlayer().displayName(), Component.text(1)));
        }
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        LinearPhaseSeries<Phase> elytraPhase = this.gameService.getElytraPhase();
        if (elytraPhase == null) return;
        var currentPhase = elytraPhase.getCurrentPhase();
        if (currentPhase == null) return;
        if (currentPhase.isRunning() && (currentPhase instanceof GamePhase || currentPhase instanceof EndPhase || currentPhase instanceof PreparationPhase)){
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, Component.empty());
            return;
        }
        if (currentPhase.isRunning() && currentPhase instanceof LobbyPhase lobbyPhase) {
            event.getPlayer().teleportAsync(lobbyPhase.getLobbyLocation());
        }
        DatabaseService databaseService = this.gameService.getDatabaseService();
        if (databaseService == null) return;
        databaseService.getElytraPlayerRepository()
                .ifPresent(elytraPlayerRepository -> elytraPlayerRepository
                        .getElytraPlayerById(event.getPlayer().getUniqueId())
                        .thenComposeAsync(databaseElytraPlayer -> {
                            if (databaseElytraPlayer == null) {
                                return elytraPlayerRepository.saveElytraPlayer(new DatabaseElytraPlayer(event.getPlayer().getUniqueId()));
                            }
                            return null;
                        }));
    }

    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        var elytraPhase = this.gameService.getElytraPhase();
        if (elytraPhase == null) return;
        var currentPhase = elytraPhase.getCurrentPhase();
        if (currentPhase == null) return;
        var cup = this.gameService.getCurrentCup().filter(ResolvedCupDTO.class::isInstance).orElse(null);
        if (cup == null) return;
        if (currentPhase instanceof LobbyPhase) {
            event.motd(Component.text("CUP: ").append(cup.displayName()));
            return;
        }
        if (currentPhase instanceof GamePhase) {
            event.motd(Component.text("IN-GAME").color(NamedTextColor.RED));
        }
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
                    }
                });
    }

}
