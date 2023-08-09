package net.elytrarace.conversation.map

import net.elytrarace.conversation.ConversationContext
import net.elytrarace.conversation.Prompt
import net.elytrarace.conversation.StringPrompt
import net.kyori.adventure.text.Component

class MapDisplayNamePrompt : StringPrompt() {
    override fun getPromptText(context: ConversationContext): Component {
        val mapName = context.getSessionData("map_name") as String
        return Component.translatable("prompt.map.name.display").args(Component.text(mapName))
    }

    override fun acceptInput(context: ConversationContext, input: String?): Prompt? {
        context.setSessionData("map_name_display", input)
        return MapWorldPrompt()
    }
}