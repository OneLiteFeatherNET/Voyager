package net.elytrarace.conversation.portal

import net.elytrarace.conversation.BooleanPrompt
import net.elytrarace.conversation.ConversationContext
import net.elytrarace.conversation.Prompt
import net.kyori.adventure.text.Component

class PortalPrompt : BooleanPrompt() {
    override fun getPromptText(context: ConversationContext): Component {
        return Component.translatable("prompt.portal")
    }

    override fun acceptValidatedInput(context: ConversationContext, input: Boolean): Prompt? {
        if (input) return PortalMapPrompt()
        return END_OF_CONVERSATION
    }
}