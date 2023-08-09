package net.elytrarace.conversation.map

import net.elytrarace.conversation.ConversationContext
import net.elytrarace.conversation.Prompt
import net.elytrarace.conversation.StringPrompt
import net.kyori.adventure.text.Component

class MapNamePrompt : StringPrompt() {
    override fun getPromptText(context: ConversationContext): Component {
        return Component.translatable("prompt.map.name")
    }

    override fun acceptInput(context: ConversationContext, input: String?): Prompt? {
        context.setSessionData("map_name", input)
        return MapDisplayNamePrompt()
    }
}