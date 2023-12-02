
package ru.idfedorov09.telegram.bot.fetchers.bot

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import ru.idfedorov09.telegram.bot.executor.Executor
import ru.idfedorov09.telegram.bot.executor.TelegramPollingBot
import ru.idfedorov09.telegram.bot.util.UpdatesUtil
import ru.mephi.sno.libs.flow.belly.InjectData
import ru.mephi.sno.libs.flow.fetcher.GeneralFetcher

@Component
class TestFetcher(
    private val updatesUtil: UpdatesUtil,
    private val bot: Executor,
) : GeneralFetcher() {
    companion object {
        private val log = LoggerFactory.getLogger(TestFetcher::class.java)
    }

    @InjectData
    fun doFetch(
        update: Update,
    ) {
        log.info("UPDATE RECEIVE: {}", update)
        bot.execute(
            SendMessage(
                updatesUtil.getChatId(update)!!,
                "ok!!",
            ),
        )
    }
}
