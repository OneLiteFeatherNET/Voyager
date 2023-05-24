package net.elytrarace.utils.i18n

import net.kyori.adventure.text.minimessage.Context
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue
import net.kyori.adventure.translation.Translator
import java.util.*
import java.util.function.BiFunction

internal object LocalizeTag : BiFunction<ArgumentQueue, Context, Tag> {
    override fun apply(args: ArgumentQueue, ctx: Context): Tag {
        // <i18n:LOCALE:KEY:args>
        val key = args.popOr("i18n tag required").value()
        val locale: Locale = Translator.parseLocale(args.pop().value()) ?: Locale.ENGLISH
        val with: List<String>
        if (args.hasNext()) {
            with = ArrayList<String>()
            while (args.hasNext()) {
                with.add(args.pop().value())
            }
        } else {
            with = emptyList()
        }
        val translation = I18n.translationRegistry.translate(key, locale)?.format(with.toTypedArray(), StringBuffer(), null).toString()

        return Tag.selfClosingInserting(ctx.deserialize(translation))
    }
}