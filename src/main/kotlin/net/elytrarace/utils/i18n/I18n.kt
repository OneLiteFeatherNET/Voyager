package net.elytrarace.utils.i18n

import net.elytrarace.utils.NAMESPACED_KEY
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags
import net.kyori.adventure.text.minimessage.tree.Node
import net.kyori.adventure.translation.TranslationRegistry

object I18n : MiniMessage {

    private val tagResolver = TagResolver.builder().resolver(StandardTags.defaults()).tag("i18n", LocalizeTag).tag("prefix", PrefixTag).build()
    private val miniMessage = MiniMessage.builder().tags(tagResolver).build()
    val translationRegistry: TranslationRegistry = TranslationRegistry.create(NAMESPACED_KEY)
    override fun deserialize(input: String, tagResolver: TagResolver): Component =
        miniMessage.deserialize(input, tagResolver)

    override fun deserialize(input: String): Component = miniMessage.deserialize(input)

    override fun serialize(component: Component): String = miniMessage.serialize(component)

    override fun escapeTags(input: String): String = miniMessage.escapeTags(input)

    override fun escapeTags(input: String, tagResolver: TagResolver): String =
        miniMessage.escapeTags(input, tagResolver)

    override fun stripTags(input: String): String = miniMessage.stripTags(input)

    override fun stripTags(input: String, tagResolver: TagResolver): String = miniMessage.stripTags(input, tagResolver)

    override fun deserializeToTree(input: String): Node.Root = miniMessage.deserializeToTree(input)

    override fun deserializeToTree(input: String, tagResolver: TagResolver): Node.Root =
        miniMessage.deserializeToTree(input, tagResolver)
}