package ru.idfedorov09.telegram.bot.data.model

data class Cd2bError(
    val statusCode: Int,
    val statusDescription: String,
    val stackTrace: String,
)
