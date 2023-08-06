package net.elytrarace.conversation.portal

import net.elytrarace.conversation.BooleanPrompt
import net.elytrarace.conversation.ConversationContext
import net.elytrarace.conversation.Prompt
import net.elytrarace.conversation.portal.add.PortalMapPrompt
import net.elytrarace.conversation.portal.edit.PortalEditPrompt
import net.kyori.adventure.text.Component

class PortalPrompt : BooleanPrompt() {
    override fun getPromptText(context: ConversationContext): Component {
        return Component.translatable("prompt.portal")
    }

    override fun acceptValidatedInput(context: ConversationContext, input: Boolean): Prompt? {
        if (input) return PortalMapPrompt()
        return PortalEditPrompt()
    }
}