package ru.idfedorov09.telegram.bot.flow

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.idfedorov09.telegram.bot.data.GlobalConstants.QUALIFIER_FLOW_TG_BOT
import ru.idfedorov09.telegram.bot.data.enums.BotStage
import ru.idfedorov09.telegram.bot.fetchers.bot.TestFetcher
import ru.idfedorov09.telegram.bot.fetchers.bot.ToggleStageFetcher
import ru.mephi.sno.libs.flow.belly.FlowBuilder

/**
 * Основной класс, в котором строится последовательность вычислений (граф) для бота
 */
@Configuration
open class TelegramBotFlowConfiguration(
    private val testFetcher: TestFetcher,
    private val toggleStageFetcher: ToggleStageFetcher,
) {

    /**
     * Возвращает построенный граф; выполняется только при запуске приложения
     */
    @Bean(QUALIFIER_FLOW_TG_BOT)
    open fun flowBuilder(): FlowBuilder {
        val flowBuilder = FlowBuilder()
        flowBuilder.buildFlow()
        return flowBuilder
    }

    private fun FlowBuilder.buildFlow() {
        group {
            fetch(toggleStageFetcher)
            whenComplete(condition = { it.get<ExpContainer>()?.botStage == BotStage.APPEAL }) {
                fetch(testFetcher)
            }
        }
    }
}
