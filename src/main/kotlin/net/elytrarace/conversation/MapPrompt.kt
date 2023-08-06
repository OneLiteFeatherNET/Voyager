package net.elytrarace.conversation

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage


class MapPrompt : BooleanPrompt() {
    override fun getPromptText(context: ConversationContext): Component {
        return MiniMessage.miniMessage().deserialize("<lang:prompt.world>")
    }

    override fun acceptValidatedInput(context: ConversationContext, input: Boolean): Prompt? {
        if (!input) {
            return END_OF_CONVERSATION
        }
        return MapCupNamePrompt()
    }
}