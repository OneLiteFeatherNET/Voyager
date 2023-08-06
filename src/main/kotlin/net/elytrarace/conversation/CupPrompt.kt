package net.elytrarace.conversation

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage


class CupPrompt : BooleanPrompt() {
    override fun getPromptText(context: ConversationContext): Component {
        return MiniMessage.miniMessage().deserialize("<lang:prompt.cup>")
    }

    override fun acceptValidatedInput(context: ConversationContext, input: Boolean): Prompt? {
        context.setSessionData("cup", input)
        return MapPrompt()
    }
}