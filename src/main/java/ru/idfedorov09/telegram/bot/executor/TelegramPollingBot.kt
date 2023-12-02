package ru.idfedorov09.telegram.bot.executor

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import ru.idfedorov09.telegram.bot.config.BotContainer
import ru.idfedorov09.telegram.bot.util.OnReceiver

@Component
@ConditionalOnProperty(name = ["telegram.bot.interaction-method"], havingValue = "polling", matchIfMissing = true)
class TelegramPollingBot(
    private val executor: Executor,
    private val botContainer: BotContainer,
    private val updateReceiver: OnReceiver,
) : TelegramLongPollingBot() {

    companion object {
        private val log = LoggerFactory.getLogger(TelegramPollingBot::class.java)
    }

    @PostConstruct
    fun postConstruct() {
        log.info("polling started.")
    }

    override fun onUpdateReceived(update: Update) {
        updateReceiver.onReceive(update, executor)
    }

    override fun getBotUsername(): String {
        return botContainer.BOT_NAME
    }

    override fun getBotToken(): String {
        return botContainer.BOT_TOKEN
    }

    @PostConstruct
    fun botConnect() {
        lateinit var telegramBotsApi: TelegramBotsApi

        try {
            telegramBotsApi = TelegramBotsApi(DefaultBotSession::class.java)
        } catch (e: TelegramApiException) {
            log.error("Can't create API: $e. Trying to reconnect..")
            botConnect()
        }

        try {
            telegramBotsApi.registerBot(this)
            log.info("TelegramAPI started. Look for messages")
        } catch (e: TelegramApiException) {
            log.error("Can't Connect. Pause " + botContainer.RECONNECT_PAUSE / 1000 + "sec and try again. Error: " + e.message)
            try {
                Thread.sleep(botContainer.RECONNECT_PAUSE.toLong())
            } catch (threadError: InterruptedException) {
                log.error(threadError.message)
                return
            }
            botConnect()
        }
    }
}
