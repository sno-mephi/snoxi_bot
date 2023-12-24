package ru.idfedorov09.telegram.bot.data.model

import jakarta.persistence.*
import ru.idfedorov09.telegram.bot.data.enums.ReleaseStages

/**
 * Таблица с историями релизов
 */
@Entity
@Table(name = "releases_history")
data class ReleaseHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    /** Имя опорного профиля **/
    @Column(name = "profile_name", columnDefinition = "TEXT")
    val profileName: String? = null,

    /** Что происходит **/
    @Enumerated(EnumType.STRING)
    @Column(name = "stage", columnDefinition = "TEXT")
    val stage: ReleaseStages? = null,

    /** Что происходит **/
    @Column(name = "is_finished")
    val isFinished: Boolean = false,

    /** Что происходит **/
    @Column(name = "commit_hash", columnDefinition = "TEXT")
    val commitHash: String? = null,

    // TODO: дата события
)