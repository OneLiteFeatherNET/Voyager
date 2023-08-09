package net.elytrarace.conversation.portal.edit

import net.elytrarace.conversation.ConversationContext
import net.elytrarace.conversation.NumericPrompt
import net.elytrarace.conversation.Prompt
import net.kyori.adventure.text.Component

class PortalEditPrompt : NumericPrompt() {
    override fun getPromptText(context: ConversationContext): Component {
        return Component.translatable("prompt.portal.edit")
    }

    override fun acceptValidatedInput(context: ConversationContext, input: Number): Prompt? {
        context.setSessionData("edit", input)
        val prompt = when(input) {
            1 -> PortalSelectMapPrompt()
            2 -> PortalSelectMapPrompt()
            3 -> END_OF_CONVERSATION
            else -> PortalEditPrompt()
        }
        return prompt
    }
}