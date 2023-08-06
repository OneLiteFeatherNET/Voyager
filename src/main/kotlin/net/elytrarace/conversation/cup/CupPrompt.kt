package net.elytrarace.conversation.cup

import net.elytrarace.conversation.BooleanPrompt
import net.elytrarace.conversation.ConversationContext
import net.elytrarace.conversation.Prompt
import net.kyori.adventure.text.Component


class CupPrompt : BooleanPrompt() {
    override fun getPromptText(context: ConversationContext): Component {
        return Component.translatable("prompt.cup")
    }

    override fun acceptValidatedInput(context: ConversationContext, input: Boolean): Prompt? {
        if (input) {
            return CupNamePrompt()
        }
        return END_OF_CONVERSATION
    }
}