package net.elytrarace.conversation.portal

import net.elytrarace.conversation.ConversationContext
import net.elytrarace.conversation.NumericPrompt
import net.elytrarace.conversation.Prompt
import net.kyori.adventure.text.Component

class PortalCountPrompt : NumericPrompt() {
    override fun getPromptText(context: ConversationContext): Component {
        context.forWhom.sendMessage(Component.translatable("suggest.worldedit.tool"))
        context.forWhom.sendMessage(Component.translatable("suggest.worldedit.selection"))
        return Component.translatable("prompt.portal.count")
    }

    override fun acceptValidatedInput(context: ConversationContext, input: Number): Prompt? {
        context.setSessionData("count", input)
        context.setSessionData("index", 1)
        return PortalSetupPrompt()
    }
}