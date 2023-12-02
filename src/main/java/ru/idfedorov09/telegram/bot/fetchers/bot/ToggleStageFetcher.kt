package ru.idfedorov09.telegram.bot.fetchers.bot

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Update
import ru.idfedorov09.telegram.bot.data.enums.BotStage
import ru.idfedorov09.telegram.bot.executor.TelegramPollingBot
import ru.idfedorov09.telegram.bot.flow.ExpContainer
import ru.idfedorov09.telegram.bot.service.RedisService
import ru.mephi.sno.libs.flow.belly.InjectData
import ru.mephi.sno.libs.flow.fetcher.GeneralFetcher

@Component
class ToggleStageFetcher(
    private val redisService: RedisService,
) : GeneralFetcher() {
    companion object {
        private val log = LoggerFactory.getLogger(ToggleStageFetcher::class.java)
    }

    @InjectData
    fun doFetch(
        exp: ExpContainer,
    ) {
        exp.botStage = redisService.getSafe("TEST____stageeeee").let {
            if (it == null) {
                exp.botStage
            } else {
                BotStage.valueOf(it)
            }
        }

        if (exp.botStage == BotStage.GAME) {
            exp.botStage = BotStage.APPEAL
        } else {
            exp.botStage = BotStage.GAME
        }
        redisService.setValue("TEST____stageeeee", exp.botStage.name)
        log.info("changed botStage to {}", exp.botStage)
    }
}
