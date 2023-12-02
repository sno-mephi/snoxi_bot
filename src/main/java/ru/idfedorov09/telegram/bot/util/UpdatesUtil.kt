package ru.idfedorov09.telegram.bot.util

import com.google.gson.Gson
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User
import ru.idfedorov09.telegram.bot.config.BotContainer
import ru.idfedorov09.telegram.bot.service.RedisService
import java.util.regex.Pattern

@Component
class UpdatesUtil(
    private val gson: Gson,
    private val redisService: RedisService,
    private val botContainer: BotContainer,
) {
    companion object {
        private val log = LoggerFactory.getLogger(UpdatesUtil::class.java)
    }

    /** Получает id чата в котором написали сообщение. Если это лс то возвращает tui**/
    fun getChatId(update: Update?): String? {
        return getByPattern(update, "\"chat\"\\s*:\\s*\\{\"id\"\\s*:\\s*(-?\\d+)")
    }

    /** Получает id пользователя с которым связано обновление **/
    fun getUserId(update: Update?): String? {
        return getByPattern(update, "\"from\"\\s*:\\s*\\{\"id\"\\s*:\\s*(\\d+)")
    }

    /** Получает текст сообщения/обновления **/
    fun getText(update: Update?): String? {
        var text = getByPattern(update, "\"text\"\\s*:\\s*\"(.+?)\"")
        if (text == null) text = getByPattern(update, "\"caption\"\\s*:\\s*\"(.+?)\"")
        return text
    }

    /** Получает юзера который инициировал апдейт как объект **/
    fun getUser(update: Update?): User? {
        val updateJson = gson.toJson(update)
        val regex = Regex("\"from\":\\{([^\\}]+)\\}")
        val matchResult = regex.find(updateJson)

        // TODO: нормальная обработка ошибок
        try {
            val userJson = "{${matchResult?.groups?.get(1)?.value}}"
            val user = gson.fromJson(userJson, User::class.java)
            return user
        } catch (e: Exception) {
            log.warn("Can't parse User by update: $update. Error:\n$e")
        }
        return null
    }

    fun getByPattern(update: Update?, pattern: String): String? {
        val updateJson = gson.toJson(update)
        var result: String? = null
        val r = Pattern.compile(pattern)
        val matcher = r.matcher(updateJson)
        if (matcher.find()) {
            result = matcher.group(1)
        }
        return result
    }

    fun getChatKey(chatId: String): String {
        return "cht_num_$chatId"
    }

    private fun removeKeyPrefix(prefix: String) {
        val keys = redisService.keys("$prefix*")
        if (!keys.isNullOrEmpty()) {
            redisService.del(keys)
        }
    }

    @PostConstruct
    fun clearAllQues() {
        log.info("Removing old ques data..")
        removeKeyPrefix("cht_num_")
        removeKeyPrefix(botContainer.messageQueuePrefix)
        log.info("Removed.")
    }
}
