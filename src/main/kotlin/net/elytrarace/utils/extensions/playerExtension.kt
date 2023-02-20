package net.elytrarace.utils.extensions

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player

fun Player.sendMiniMessage(value: String) {
    this.sendMessage(MiniMessage.miniMessage().deserialize(value))
}
fun Player.sendFormattedMiniMessage(format: String, vararg args: Any?) {
    this.sendMiniMessage(String.format(format, *args))
}
