package ru.idfedorov09.telegram.bot.data.model

import jakarta.persistence.*

/**
 * Таблица для обработки коллбэков
 */
@Entity
@Table(name = "snoxi_callback_data_table")
data class CallbackData(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    /** id сообщения кнопки **/
    @Column(name = "msg_id")
    val messageId: String? = null,

    /** информация, хранящаяся в коллбеке **/
    @Column(name = "callback_data", columnDefinition = "TEXT")
    val callbackData: String? = null,
)