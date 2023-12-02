package ru.idfedorov09.telegram.bot.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileResponse(
    val name: String,

    @SerialName("repo_name")
    val repoName: String,

    @SerialName("repo_uri")
    val repoUri: String,

    val port: Int,

    @SerialName("image_name")
    val imageName: String,

    @SerialName("is_running")
    val isRunning: Boolean,
)
