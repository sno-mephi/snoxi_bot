package ru.idfedorov09.telegram.bot.executor

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.objects.Update
import ru.idfedorov09.telegram.bot.config.BotContainer

@Component
class Executor(
    private val botContainer: BotContainer,
) : TelegramLongPollingBot() {

    companion object {
        private val log = LoggerFactory.getLogger(Executor::class.java)
    }

    // TODO: выполнить подключение?
    @PostConstruct
    fun postConstruct() {
        log.info("Telegram method executor created.")
    }

    override fun onUpdateReceived(update: Update) {}
    override fun getBotUsername(): String {
        return botContainer.BOT_NAME
    }

    override fun getBotToken(): String {
        return botContainer.BOT_TOKEN
    }
}
