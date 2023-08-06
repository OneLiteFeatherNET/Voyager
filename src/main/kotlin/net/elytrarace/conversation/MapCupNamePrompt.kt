package net.elytrarace.conversation

import net.elytrarace.model.dbo.Cup
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.jetbrains.exposed.sql.transactions.transaction

class MapCupNamePrompt : StringPrompt() {
    override fun getPromptText(context: ConversationContext): Component {
        return MiniMessage.miniMessage().deserialize("<lang:prompt.map.cup.name>")
    }

    override fun acceptInput(context: ConversationContext, input: String?): Prompt? {
        context.setSessionData("map_cup_name", input)
        return MapNamePrompt()
    }

    override fun suggestions(): Collection<String> = transaction {
        return@transaction Cup.all().map { it.name }.toCollection(mutableListOf())
    }
}