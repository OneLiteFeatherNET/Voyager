package net.elytrarace.conversation

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage

class FinishPrompt : MessagePrompt() {
    override fun getPromptText(context: ConversationContext): Component {
        return Component.translatable("prompt.finish")
    }

    override fun getNextPrompt(context: ConversationContext): Prompt? {
        return END_OF_CONVERSATION
    }
}