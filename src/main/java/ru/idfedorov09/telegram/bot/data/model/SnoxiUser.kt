package ru.idfedorov09.telegram.bot.data.model

import jakarta.persistence.*
import ru.idfedorov09.telegram.bot.data.enums.UserActionType

/**
 * Таблица с доп. полями специально для Snoxi
 */
@Entity
@Table(name = "snoxi_users_table")
data class SnoxiUser(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    /** id юзера в телеграме **/
    @Column(name = "tui", unique = true)
    val tui: String? = null,

    /** тип следующего (текущего) действия пользователя **/
    @Enumerated(EnumType.STRING)
    @Column(name = "current_action_type")
    val currentUserActionType: UserActionType? = null,

    /** доп. поле с инфой о том что происходит **/
    @Column(name = "user_data", columnDefinition = "TEXT")
    val data: String? = null,
)
