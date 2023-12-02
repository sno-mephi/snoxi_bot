package ru.idfedorov09.telegram.bot.controller

import kotlinx.coroutines.Dispatchers
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import ru.idfedorov09.telegram.bot.data.GlobalConstants.HEALTH_PATH
import ru.idfedorov09.telegram.bot.data.GlobalConstants.QUALIFIER_FLOW_HEALTH_STATUS
import ru.idfedorov09.telegram.bot.data.model.HealthStatus
import ru.idfedorov09.telegram.bot.data.model.HealthStatus.Messages.HEALTH_FLOW_NOT_CONTAINS_STATUS
import ru.mephi.sno.libs.flow.belly.FlowBuilder
import ru.mephi.sno.libs.flow.belly.FlowContext

@RestController
class HealthStatusController {

    @Autowired
    @Qualifier(QUALIFIER_FLOW_HEALTH_STATUS)
    private lateinit var flowBuilder: FlowBuilder

    // TODO: узнать статус бота (пройтись по редису, постгресу и прочему, собрать ошибки и тд)
    @GetMapping(HEALTH_PATH)
    fun healthStatusCheck(): HealthStatus {
        // контекст для флоу сбора статуса работы бота
        val flowContext = FlowContext()

        flowBuilder.initAndRun(
            flowContext = flowContext,
            dispatcher = Dispatchers.Default,
        )

        return flowContext.get<HealthStatus>()
            ?: throw NoSuchFieldException(HEALTH_FLOW_NOT_CONTAINS_STATUS)
    }
}
