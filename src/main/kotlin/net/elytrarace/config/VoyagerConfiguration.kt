package net.elytrarace.config

import kotlinx.serialization.Serializable

@Serializable
data class VoyagerConfiguration(
    val sqlConfig: SQLConfig,
    val pluginMode: PluginMode
)