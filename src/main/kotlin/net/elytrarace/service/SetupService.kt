package net.elytrarace.service

import net.elytrarace.Voyager
import net.elytrarace.conversation.ConversationAbandonedEvent
import net.elytrarace.conversation.ConversationFactory
import net.elytrarace.conversation.Prompt
import net.elytrarace.model.dto.SetupPlayer
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player

class SetupService(val voyager: Voyager) {

    val setupPlayers: MutableList<SetupPlayer> = mutableListOf()

    fun startSetup(player: Player, prompt: Prompt) {
        val setupPlayer = SetupPlayer(player)
        ConversationFactory(voyager).withFirstPrompt(prompt).withPrefix { MiniMessage.miniMessage().deserialize("<lang:plugin.prefix>") }.addConversationAbandonedListener(this::remove).buildConversation(setupPlayer).begin()
        setupPlayers.add(setupPlayer)
    }

    private fun remove(conversationAbandonedEvent: ConversationAbandonedEvent) {
        this.setupPlayers.removeIf {
            it == conversationAbandonedEvent.context.forWhom
        }
    }

}