package ru.idfedorov09.telegram.bot.data.model

import jakarta.persistence.*

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
)
