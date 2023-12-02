package ru.idfedorov09.telegram.bot.util

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.objects.Update
import ru.idfedorov09.telegram.bot.controller.UpdatesController
import ru.idfedorov09.telegram.bot.service.RedisService
import ru.idfedorov09.telegram.bot.service.UserQueue
import java.util.concurrent.Executors

@Component
class OnReceiver(
    private val redisService: RedisService,
    private val updatesUtil: UpdatesUtil,
    private val userQueue: UserQueue,
    private val updatesHandler: UpdatesController,
) {

    companion object {
        private val log = LoggerFactory.getLogger(OnReceiver::class.java)
    }

    private val updatingRequestDispatcher = Executors.newFixedThreadPool(Int.MAX_VALUE).asCoroutineDispatcher()

    /** Обрабатывает отдельное обновление **/
    private fun execOne(update: Update, executor: TelegramLongPollingBot) {
        log.info("Update received: $update")
        updatesHandler.handle(executor, update)
    }

    /** Обрабатывает пришедшее обновление, затем обрабатывая все, что есть в очереди **/
    private fun exec(update: Update, executor: TelegramLongPollingBot) {
        val chatId = updatesUtil.getChatId(update)

        if (chatId == null) {
            execOne(update, executor)
            return
        }

        val chatKey = updatesUtil.getChatKey(chatId)

        if (redisService.getSafe(chatKey) == null) {
            redisService.setValue(chatKey, "1")
            execOne(update, executor)
            redisService.del(chatKey)

            val upd = userQueue.popUpdate(chatId)
            upd?.let { onReceive(upd, executor) }
        } else {
            userQueue.push(update, chatId)
        }
    }

    // TODO: убрать GlobalScope
    @OptIn(DelicateCoroutinesApi::class)
    fun onReceive(update: Update, executor: TelegramLongPollingBot) {
        GlobalScope.launch(updatingRequestDispatcher) {
            exec(update, executor)
        }
    }
}
