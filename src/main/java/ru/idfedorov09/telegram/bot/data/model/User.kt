package ru.idfedorov09.telegram.bot.data.model

import jakarta.persistence.*

@Entity
@Table(name = "users_table")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    /** id юзера в телеграме **/
    @Column(name = "tui", unique = true)
    val tui: String? = null,

    /** последний сохраненный ник в телеге **/
    @Column(name = "last_tg_nick")
    val lastTgNick: String? = null,

    /** ФИО **/
    @Column(name = "full_name")
    val fullName: String? = null,

    /** учебная группа **/
    @Column(name = "study_group")
    val studyGroup: String? = null,

    /** роли **/
    @Column(name = "roles")
    val roles: MutableSet<String> = mutableSetOf()
)
