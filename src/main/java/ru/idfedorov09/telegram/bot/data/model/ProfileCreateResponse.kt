package ru.idfedorov09.telegram.bot.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ProfileCreateResponse(
    val message: String,
    val profile: ProfileResponse,
)
