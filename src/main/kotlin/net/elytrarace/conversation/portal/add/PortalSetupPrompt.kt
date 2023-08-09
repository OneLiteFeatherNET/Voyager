package net.elytrarace.conversation.portal.add

import com.fastasyncworldedit.core.regions.PolyhedralRegion
import com.sk89q.worldedit.IncompleteRegionException
import com.sk89q.worldedit.bukkit.BukkitAdapter
import net.elytrarace.conversation.BooleanPrompt
import net.elytrarace.conversation.ConversationContext
import net.elytrarace.conversation.FinishPrompt
import net.elytrarace.conversation.Prompt
import net.elytrarace.model.dbo.ElytraMap
import net.elytrarace.model.dbo.Portal
import net.elytrarace.model.dbo.PortalLocation
import net.elytrarace.model.dto.SetupPlayer
import net.elytrarace.utils.MINIMUM_POINT
import net.kyori.adventure.text.Component
import org.jetbrains.exposed.sql.transactions.transaction

class PortalSetupPrompt : BooleanPrompt() {
    override fun getPromptText(context: ConversationContext): Component {
        val index = context.getSessionData("index") as Int
        return Component.translatable("prompt.portal.setup").args(Component.text(index))
    }

    override fun acceptValidatedInput(context: ConversationContext, input: Boolean): Prompt? {
        if (!input) {
            return PortalSetupPrompt()
        }
        context.setSessionData("portal_$input", input)
        val index = context.getSessionData("index") as Int
        val count = context.getSessionData("count") as Int
        val player = (context.forWhom as SetupPlayer).player
        val actor = BukkitAdapter.adapt(player)
        val localSession = actor.session
        val selectionWorld = localSession.selectionWorld ?: throw IncompleteRegionException()
        val region = localSession.getSelection(selectionWorld)
        if (region is PolyhedralRegion) {
            if (region.vertices.size < MINIMUM_POINT) {
                return PortalSetupPrompt()
            }
            val map = context.getSessionData("map") as ElytraMap
            transaction {
                val portal = Portal.new {
                    this.index = index
                    this.map = map
                }
                region.vertices.forEach {
                    PortalLocation.new {
                        x = it.x
                        y = it.y
                        z = it.z
                        this.portal = portal
                    }
                }
                PortalLocation.new {
                    x = region.center.blockX
                    y = region.center.blockY
                    z = region.center.blockZ
                    center = true
                    this.portal = portal
                }
            }
        }
        context.setSessionData("index", index + 1)
        if (index + 1 == count + 1) {
            return FinishPrompt()
        }
        return PortalSetupPrompt()
    }
}