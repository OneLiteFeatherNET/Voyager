package net.elytrarace.conversation

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage

class MapDisplayNamePrompt : StringPrompt() {
    override fun getPromptText(context: ConversationContext): Component {
        val mapName = context.getSessionData("map_name") as String
        return MiniMessage.miniMessage().deserialize("<lang:prompt.map.name.display:$mapName>")
    }

    override fun acceptInput(context: ConversationContext, input: String?): Prompt? {
        context.setSessionData("map_name_display", input)
        return MapWorldPrompt()
    }
}