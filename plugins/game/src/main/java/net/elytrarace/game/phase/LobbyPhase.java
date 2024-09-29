package net.elytrarace.game.phase;

import net.elytrarace.api.phase.TickDirection;
import net.elytrarace.api.phase.TimedPhase;
import net.elytrarace.common.utils.Strings;
import net.elytrarace.common.utils.TimeFormat;
import net.elytrarace.game.model.GameMapDTO;
import net.elytrarace.game.model.GameSession;
import net.elytrarace.game.service.GameService;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class LobbyPhase extends TimedPhase {

    private static final ComponentLogger LOGGER = ComponentLogger.logger(LobbyPhase.class);
    private static final int LOBBY_TIME = 120;
    private final GameService gameService;
    private final World lobbyWorld;

    public LobbyPhase(GameService gameService) {
        super("Lobby", gameService.getPlugin(), 20, true);
        this.gameService = gameService;
        this.lobbyWorld = Bukkit.getWorlds().getFirst();
        setEndTicks(0);
        setTickDirection(TickDirection.DOWN);
    }

    @Override
    public void onStart() {
        setCurrentTicks(LOBBY_TIME);
        super.onStart();
    }

    @Override
    public void onUpdate() {
        var players = Bukkit.getOnlinePlayers();
        var formattedTime = Strings.getTimeString(TimeFormat.MM_SS, getCurrentTicks());
        var sound = switch (getCurrentTicks()) {
            case 60, 30, 15, 10, 5, 4, 3, 2, 1 ->
                    Sound.sound(this::configureLobbySound);
            default -> null;
        };
        players.forEach(player -> {
            if (sound != null) {
                player.playSound(sound);
            }
            player.sendActionBar(Component.translatable("phase.lobby.time", Component.text(formattedTime)));
        });
    }

    private void configureLobbySound(Sound.Builder builder) {
        builder.type(org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS);
        builder.volume(2.0f);
    }

    @Override
    protected void onFinish() {
        this.gameService.switchMap()
                .thenApply(GameSession::currentMap)
                .thenCompose(this::teleportPlayers)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to switch map", throwable);
                    return null;
                });
    }

    private CompletableFuture<Void> teleportPlayers(GameMapDTO gameMapDTO) {
        var completableFutures = Bukkit.getOnlinePlayers().stream().map(player ->
                player.teleportAsync(gameMapDTO.bukkitWorld().getSpawnLocation())
                        .exceptionally(throwable -> {
                            LOGGER.error("Failed to teleport player to map", throwable);
                            return null;
                        })).toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(completableFutures);
    }

    public @NotNull Location getLobbyLocation() {
        return this.lobbyWorld.getSpawnLocation();
    }
}
