package ru.idfedorov09.telegram.bot.data.model

data class UserActualizedInfo(
    val snoxiId: Long? = null,
    val tui: String,
    val lastTgNick: String? = null,
    val fullName: String? = null,
    val studyGroup: String? = null,
    val roles: MutableSet<String> = mutableSetOf()
)