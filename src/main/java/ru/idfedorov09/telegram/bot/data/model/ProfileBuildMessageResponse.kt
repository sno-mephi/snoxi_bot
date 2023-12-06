package ru.idfedorov09.telegram.bot.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileBuildMessageResponse(
    val message: String,
    @SerialName("is_new_line")
    val isNewLine: Boolean
)
