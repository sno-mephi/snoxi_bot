package ru.idfedorov09.telegram.bot.service

import com.google.gson.Gson
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.objects.Update
import ru.idfedorov09.telegram.bot.config.BotContainer

@Service
class UserQueue(
    private val redisService: RedisService,
    private val gson: Gson,
    private val botContainer: BotContainer,
) {

    fun getQueueKey(chatId: String): String {
        return botContainer.messageQueuePrefix + chatId
    }

    fun popString(chatId: String): String? {
        return redisService.lpop(getQueueKey(chatId))
    }

    fun popUpdate(chatId: String): Update? {
        val jsonUpdate = popString(chatId) ?: return null
        return gson.fromJson(jsonUpdate, Update::class.java)
    }

    fun push(update: Update?, chatId: String) {
        val jsonUpdate = gson.toJson(update, Update::class.java)
        this.push(jsonUpdate, chatId)
    }

    fun push(string: String?, chatId: String) {
        redisService.rpush(getQueueKey(chatId), string!!)
    }
}
