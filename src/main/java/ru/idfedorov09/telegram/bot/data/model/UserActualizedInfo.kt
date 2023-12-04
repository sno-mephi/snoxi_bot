package ru.idfedorov09.telegram.bot.data.model

import ru.idfedorov09.telegram.bot.data.enums.UserActionType
import ru.mephi.sno.libs.flow.belly.Mutable

@Mutable
data class UserActualizedInfo(
    val snoxiId: Long? = null,
    val tui: String,
    val lastTgNick: String? = null,
    val fullName: String? = null,
    val studyGroup: String? = null,
    val roles: MutableSet<String> = mutableSetOf(),

    var currentActionType: UserActionType? = null,
    var data: String? = null,
)