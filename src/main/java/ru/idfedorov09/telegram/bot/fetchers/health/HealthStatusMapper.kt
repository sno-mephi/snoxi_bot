package ru.idfedorov09.telegram.bot.fetchers.health

import org.springframework.stereotype.Component
import ru.idfedorov09.telegram.bot.data.model.HealthStatus
import ru.mephi.sno.libs.flow.belly.InjectData
import ru.mephi.sno.libs.flow.fetcher.GeneralFetcher

@Component
class HealthStatusMapper : GeneralFetcher() {

    @InjectData
    fun doFetch(): HealthStatus {
        return HealthStatus("is alive")
    }
}
