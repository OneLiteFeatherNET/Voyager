package net.elytrarace.listener

import io.papermc.paper.event.player.AsyncChatEvent
import net.elytrarace.Voyager
import net.elytrarace.model.dto.SetupPlayer
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class SetupListener(val voyager: Voyager) : Listener {

    @EventHandler
    fun chatListening(event: AsyncChatEvent) {
        event.viewers().removeIf(this::removeSetupPlayers)
        val player =
            voyager.setupService.setupPlayers.firstOrNull { setupPlayer: SetupPlayer -> setupPlayer.player == event.player }
                ?: return
        player.acceptConversationInput(PlainTextComponentSerializer.plainText().serialize(event.message()))
        event.isCancelled = true
    }

    private fun removeSetupPlayers(audience: Audience): Boolean {
        return voyager.setupService.setupPlayers.any { setupPlayer: SetupPlayer -> setupPlayer.player == audience }
    }
}