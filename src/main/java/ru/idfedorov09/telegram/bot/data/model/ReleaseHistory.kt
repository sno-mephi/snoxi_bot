package ru.idfedorov09.telegram.bot.data.model

import jakarta.persistence.*
import ru.idfedorov09.telegram.bot.data.enums.ReleaseStages
import java.time.LocalDateTime

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

    /** Имя первого опорного профиля **/
    @Column(name = "first_profile_name", columnDefinition = "TEXT")
    val firstProfileName: String? = null,

    /** Имя второго опорного профиля **/
    @Column(name = "second_profile_name", columnDefinition = "TEXT")
    val secondProfileName: String? = null,

    /** Имя тестового профиля **/
    @Column(name = "testing_profile_name", columnDefinition = "TEXT")
    val testProfileName: String? = null,

    /** Что происходит **/
    @Enumerated(EnumType.STRING)
    @Column(name = "stage", columnDefinition = "TEXT")
    val stage: ReleaseStages? = null,

    /** Завершилась ли раскатка на этом действии **/
    @Column(name = "is_finished")
    val isFinished: Boolean = false,

    /** Хэш актуальной версии, которая сейчас катится **/
    @Column(name = "commit_hash", columnDefinition = "TEXT")
    val commitHash: String? = null,

    /** Описание события **/
    @Column(name = "event_desc", columnDefinition = "TEXT")
    val eventDescription: String? = null,

    @Column(name = "time")
    val time: LocalDateTime? = null,
)