package net.elytrarace.utils.i18n

import net.elytrarace.utils.PREFIX
import net.kyori.adventure.text.minimessage.Context
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue
import java.util.function.BiFunction

internal object PrefixTag : BiFunction<ArgumentQueue, Context, Tag> {
    override fun apply(t: ArgumentQueue, ctx: Context): Tag {
        return Tag.selfClosingInserting(
            ctx.deserialize(
                PREFIX
            )
        )
    }
}