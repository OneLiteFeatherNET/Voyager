package net.elytrarace.conversation

import net.elytrarace.model.dbo.Cup
import net.elytrarace.model.dbo.Cups
import net.elytrarace.model.dbo.ElytraMap
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

class MapAuthorPrompt : StringPrompt() {
    override fun getPromptText(context: ConversationContext): Component {
        return MiniMessage.miniMessage().deserialize("<lang:prompt.map.author>")
    }

    override fun acceptInput(context: ConversationContext, input: String?): Prompt? {
        context.setSessionData("map_author", input)
        transaction {
            val cup = Cups.select { Cups.name eq context.getSessionData("map_cup_name") as String }.firstOrNull() ?: return@transaction null
            ElytraMap.new {
                this.cup = Cup.wrapRow(cup)
                author = input
                world = context.getSessionData("map_world") as String
                name = context.getSessionData("map_name") as String
                displayName = context.getSessionData("map_name_display") as String
            }
        }
        return FinishPrompt()
    }
}