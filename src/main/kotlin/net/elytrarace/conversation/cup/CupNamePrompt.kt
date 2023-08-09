package net.elytrarace.conversation.cup

import net.elytrarace.conversation.ConversationContext
import net.elytrarace.conversation.Prompt
import net.elytrarace.conversation.StringPrompt
import net.kyori.adventure.text.Component

class CupNamePrompt : StringPrompt() {
    override fun getPromptText(context: ConversationContext): Component {
        return Component.translatable("prompt.cup.name")
    }

    override fun acceptInput(context: ConversationContext, input: String?): Prompt? {
        context.setSessionData("cup_name", input)
        return CupDisplayNamePrompt()
    }
}