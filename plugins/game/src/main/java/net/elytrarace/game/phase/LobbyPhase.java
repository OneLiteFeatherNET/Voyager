package net.elytrarace.game.phase;

import net.elytrarace.api.phase.TickDirection;
import net.elytrarace.api.phase.TimedPhase;
import net.elytrarace.common.utils.Strings;
import net.elytrarace.common.utils.TimeFormat;
import net.elytrarace.game.service.GameService;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;

import java.util.concurrent.CompletableFuture;

public class LobbyPhase extends TimedPhase {

    private static final int LOBBY_TIME = 120;
    private final GameService gameService;

    public LobbyPhase(GameService gameService) {
        super("Lobby", gameService.getPlugin(), 20, true);
        this.gameService = gameService;
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
        this.gameService.switchMap().thenRunAsync(() -> {
            this.gameService.getCurrentMap().ifPresent(gameMapDTO -> {
                Bukkit.getOnlinePlayers().forEach(player -> {
                    System.out.println("Teleporting player " + player.getName());
                    System.out.println("World: " + gameMapDTO.bukkitWorld().getName());
                    player.teleportAsync(gameMapDTO.bukkitWorld().getSpawnLocation())
                            .exceptionally(throwable -> {
                                throwable.printStackTrace();
                                return null;
                            });
                });
            });
        }, Bukkit.getScheduler().getMainThreadExecutor(getPlugin()))
                .exceptionally(throwable -> {
                    throwable.printStackTrace();
                    return null;
                });
    }
}
