package net.elytrarace.conversation.map

import net.elytrarace.conversation.BooleanPrompt
import net.elytrarace.conversation.ConversationContext
import net.elytrarace.conversation.Prompt
import net.kyori.adventure.text.Component


class MapPrompt : BooleanPrompt() {
    override fun getPromptText(context: ConversationContext): Component {
        return Component.translatable("prompt.world")
    }

    override fun acceptValidatedInput(context: ConversationContext, input: Boolean): Prompt? {
        if (!input) {
            return END_OF_CONVERSATION
        }
        return MapCupNamePrompt()
    }
}