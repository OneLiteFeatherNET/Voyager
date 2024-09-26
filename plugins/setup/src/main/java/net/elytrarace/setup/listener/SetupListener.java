package net.elytrarace.setup.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.elytrarace.setup.ElytraRace;
import net.elytrarace.setup.model.SetupHolder;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public class SetupListener implements Listener {
    private final JavaPlugin plugin;

    public SetupListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }


    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        event.getPlayer().removeMetadata(ElytraRace.SETUP_METADATA, plugin);
    }


    @EventHandler
    public void onChat(AsyncChatEvent event) {
        // Remove all viewers that are in setup mode
        event.viewers().removeIf(this::removeSetupPlayer);

        var player = event.getPlayer();
        MetadataValue metadataValue = player.getMetadata(ElytraRace.SETUP_METADATA).getFirst();
        if (metadataValue == null) {
            return;
        }
        Optional.ofNullable(metadataValue.value())
                .filter(SetupHolder.class::isInstance)
                .map(SetupHolder.class::cast)
                .ifPresent(holder -> {
                            event.setCancelled(true);
                            holder.acceptConversationInput(PlainTextComponentSerializer.plainText().serialize(event.message()));
                });
    }

    private boolean removeSetupPlayer(Audience audience) {
        return Optional.ofNullable(audience)
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .stream().anyMatch(this::isSetupPlayer);
    }

    private boolean isSetupPlayer(Player player) {
        if (player.hasMetadata(ElytraRace.SETUP_METADATA)) {
            MetadataValue metadataValue = player.getMetadata(ElytraRace.SETUP_METADATA).getFirst();
            if (metadataValue == null) {
                return false;
            }
            return Optional.ofNullable(metadataValue.value())
                    .filter(SetupHolder.class::isInstance)
                    .map(SetupHolder.class::cast)
                    .stream().anyMatch(setupHolder -> setupHolder.getPlayer() == player);
        }
        return false;
    }

}
