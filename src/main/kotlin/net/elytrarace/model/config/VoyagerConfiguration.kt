package net.elytrarace.model.config

import kotlinx.serialization.Serializable

@Serializable
data class VoyagerConfiguration(
    val sqlConfig: SQLConfig
)