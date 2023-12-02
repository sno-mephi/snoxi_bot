package ru.idfedorov09.telegram.bot.fetchers.bot

import org.springframework.stereotype.Component
import ru.idfedorov09.telegram.bot.data.model.SnoxiUser
import ru.idfedorov09.telegram.bot.data.model.UserActualizedInfo
import ru.idfedorov09.telegram.bot.repo.SnoxiUserRepository
import ru.mephi.sno.libs.flow.belly.InjectData
import ru.mephi.sno.libs.flow.fetcher.GeneralFetcher

@Component
class UpdateDataFetcher(
    private val snoxiUserRepository: SnoxiUserRepository,
) : GeneralFetcher() {

    @InjectData
    fun doFetch(
        userActualizedInfo: UserActualizedInfo,
    ) {
        var userToSave = SnoxiUser(
            tui = userActualizedInfo.tui,
        )

        if (userActualizedInfo.snoxiId != null) userToSave = userToSave.copy(id = userActualizedInfo.snoxiId)

        snoxiUserRepository.save(
            userToSave,
        )
    }
}
