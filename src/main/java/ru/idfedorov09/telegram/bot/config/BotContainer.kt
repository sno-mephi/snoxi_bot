package ru.idfedorov09.telegram.bot.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ru.idfedorov09.telegram.bot.data.GlobalConstants.QUEUE_PRE_PREFIX

@Component
data class BotContainer(
    @Value("\${telegram.bot.token}")
    val token: String,

    @Value("\${telegram.bot.name}")
    val name: String,

    @Value("\${telegram.bot.reconnect-pause:1000}")
    val reconnectPause: Long = 1000,
) {
    val messageQueuePrefix: String
        get() = name + QUEUE_PRE_PREFIX
}
