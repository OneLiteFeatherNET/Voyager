package net.elytrarace.conversation

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage

class PortalCountPrompt : NumericPrompt() {
    override fun getPromptText(context: ConversationContext): Component {
        context.forWhom.sendMessage(MiniMessage.miniMessage().deserialize("<lang:suggest.worldedit.tool>"))
        context.forWhom.sendMessage(MiniMessage.miniMessage().deserialize("<lang:suggest.worldedit.selection>"))
        return MiniMessage.miniMessage().deserialize("<lang:prompt.portal.count>")
    }

    override fun acceptValidatedInput(context: ConversationContext, input: Number): Prompt? {
        context.setSessionData("count", input)
        context.setSessionData("index", 1)
        return PortalSetupPrompt()
    }
}