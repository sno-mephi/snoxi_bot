package ru.idfedorov09.telegram.bot.data.model

data class ProfilePropertiesBase(
    val token: String,
    val name: String,
    val port: Int,
    val postgresUrl: String,
    val postgresUsername: String,
    val postgresPassword: String,
    val redisHost: String,
    val redisPort: Int,
    val redisPassword: String,
)
