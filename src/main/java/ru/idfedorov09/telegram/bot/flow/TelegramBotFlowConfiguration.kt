package ru.idfedorov09.telegram.bot.flow

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.idfedorov09.telegram.bot.data.GlobalConstants.QUALIFIER_FLOW_TG_BOT
import ru.idfedorov09.telegram.bot.fetchers.bot.*
import ru.mephi.sno.libs.flow.belly.FlowBuilder
import ru.mephi.sno.libs.flow.belly.FlowContext

/**
 * Основной класс, в котором строится последовательность вычислений (граф) для бота
 */
@Configuration
open class TelegramBotFlowConfiguration(
    private val initialFetcher: InitialFetcher,
    private val updateDataFetcher: UpdateDataFetcher,
    private val manageProfilesFetcher: ManageProfilesFetcher,
    private val callbackValidateFetcher: CallbackValidateFetcher,
    private val featureRealiseFetcher: FutureReleaseFetcher,
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
        sequence {
            fetch(initialFetcher)
            fetch(callbackValidateFetcher)
            sequence(condition = { it.isValid() }) {
                fetch(manageProfilesFetcher)
                fetch(featureRealiseFetcher)
            }
            fetch(updateDataFetcher)
        }
    }

    private fun FlowContext.isValid() = this.get<ExpContainer>()?.isValid ?: false
}
