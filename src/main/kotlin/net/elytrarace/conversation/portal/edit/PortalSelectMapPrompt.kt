package net.elytrarace.conversation.portal.edit

import net.elytrarace.conversation.ConversationContext
import net.elytrarace.conversation.Prompt
import net.elytrarace.conversation.StringPrompt
import net.elytrarace.conversation.portal.add.PortalCountPrompt
import net.elytrarace.model.dbo.ElytraMap
import net.elytrarace.model.dbo.ElytraMaps
import net.elytrarace.model.dto.SetupPlayer
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.WorldCreator
import org.bukkit.WorldType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

class PortalSelectMapPrompt : StringPrompt() {
    override fun getPromptText(context: ConversationContext): Component {
        return Component.translatable("prompt.portal.select.map")
    }

    override fun acceptInput(context: ConversationContext, input: String?): Prompt? = transaction {
        input ?: return@transaction END_OF_CONVERSATION
        val maps = ElytraMap.wrapRows(ElytraMaps.select { ElytraMaps.name eq input })
        if (maps.empty()) {
            return@transaction END_OF_CONVERSATION
        }
        val map = maps.firstOrNull() ?: return@transaction END_OF_CONVERSATION
        context.setSessionData("map", map)
        val world = Bukkit.getScheduler().callSyncMethod(context.plugin!!) {
            Bukkit.createWorld(
                WorldCreator.name(map.world).generator("VoidGen").type(
                    WorldType.NORMAL))}.get()  ?: return@transaction END_OF_CONVERSATION
        val player = (context.forWhom as SetupPlayer).player
        player.teleportAsync(world.spawnLocation)
        return@transaction PortalCountPrompt()
    }

    override fun suggestions(): Collection<String> = transaction {
        return@transaction ElytraMap.all().map { it.name }.toCollection(mutableListOf())
    }
}