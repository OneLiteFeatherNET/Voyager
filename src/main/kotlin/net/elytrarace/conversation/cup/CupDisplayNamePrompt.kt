package net.elytrarace.conversation.cup

import net.elytrarace.conversation.ConversationContext
import net.elytrarace.conversation.FinishPrompt
import net.elytrarace.conversation.Prompt
import net.elytrarace.conversation.StringPrompt
import net.elytrarace.model.dbo.Cup
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.jetbrains.exposed.sql.transactions.transaction

class CupDisplayNamePrompt : StringPrompt() {
    override fun getPromptText(context: ConversationContext): Component {
        val name = context.getSessionData("cup_name") as String
        return Component.translatable("prompt.cup.name.display").args(Component.text(name))
    }

    override fun acceptInput(context: ConversationContext, input: String?): Prompt? {
        context.setSessionData("cup_display_name", input)
        transaction {
            Cup.new {
                name = context.getSessionData("cup_name") as String
                displayName = context.getSessionData("cup_display_name") as String
            }
        }
        return FinishPrompt()
    }
}