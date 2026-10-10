package net.elytrarace.setup.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.elytrarace.setup.ElytraRace;
import net.elytrarace.setup.model.SetupHolder;
import net.elytrarace.setup.session.SetupSessionManagerImpl;
import net.elytrarace.setup.util.FaweHelper;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

import java.util.Optional;

public class SetupListener implements Listener {
    private final ElytraRace plugin;

    public SetupListener(ElytraRace plugin) {
        this.plugin = plugin;
    }


    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        var player = event.getPlayer();
        var playerId = player.getUniqueId();

        // Persist the session before removing it, so the builder can resume on rejoin
        plugin.getSessionManager().get(playerId).ifPresent(session ->
                plugin.getSessionPersistence().save(session));
        plugin.getSessionManager().remove(playerId);

        player.removeMetadata(ElytraRace.SETUP_METADATA, plugin);
        plugin.getUndoManager().removePlayer(playerId);
        plugin.getPreviewManager().remove(playerId);
        plugin.getEditingContextManager().clearContext(playerId);
        plugin.getTestflyManager().remove(playerId);
        plugin.getWizardManager().remove(playerId);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var playerId = player.getUniqueId();

        // Attempt to restore a persisted session from a previous disconnect
        plugin.getSessionPersistence().load(playerId).ifPresent(restoredSession -> {
            // Re-register the session in the manager
            if (plugin.getSessionManager() instanceof SetupSessionManagerImpl impl) {
                impl.put(restoredSession);
            }
            // Re-create the legacy SetupHolder so existing commands keep working
            player.setMetadata(ElytraRace.SETUP_METADATA,
                    new FixedMetadataValue(plugin, new SetupHolder(playerId)));
            player.sendActionBar(Component.translatable("setup.session.restored"));

            // Restore preview preferences on the main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (restoredSession.portalPreviewEnabled()) {
                    plugin.getPreviewManager().togglePortals(playerId);
                }
                if (restoredSession.splinePreviewEnabled()) {
                    plugin.getPreviewManager().toggleSpline(playerId);
                }
            });
        });
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        var player = event.getPlayer();
        if (!player.hasMetadata(ElytraRace.SETUP_METADATA)) return;
        FaweHelper.resetToPolyhedralSelector(player);
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
