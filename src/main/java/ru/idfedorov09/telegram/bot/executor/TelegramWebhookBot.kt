package ru.idfedorov09.telegram.bot.executor

import com.google.gson.Gson
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.telegram.telegrambots.meta.api.objects.Update
import ru.idfedorov09.telegram.bot.data.GlobalConstants.WEBHOOK_PATH
import ru.idfedorov09.telegram.bot.util.OnReceiver

@Controller
@ConditionalOnProperty(name = ["telegram.bot.interaction-method"], havingValue = "webhook", matchIfMissing = false)
class TelegramWebhookBot(
    private val executor: Executor,
    private val onReceiver: OnReceiver,
    private val gson: Gson,
) {

    companion object {
        private val log = LoggerFactory.getLogger(TelegramWebhookBot::class.java)
    }

    @PostConstruct
    fun postConstruct() {
        log.info("webhook started.")
    }

    @PostMapping(WEBHOOK_PATH)
    @ResponseStatus(value = HttpStatus.OK)
    fun handler(@RequestBody jsonUpdate: String?) {
        val update = gson.fromJson(jsonUpdate, Update::class.java)
        onReceiver.onReceive(update, executor)
    }
}
