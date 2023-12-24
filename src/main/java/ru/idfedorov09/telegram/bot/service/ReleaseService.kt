package ru.idfedorov09.telegram.bot.service

import org.springframework.stereotype.Component
import ru.idfedorov09.telegram.bot.data.GlobalConstants.REDIS_REALISE_REFPROFILE_NAME
import ru.idfedorov09.telegram.bot.data.enums.ReleaseStages
import ru.idfedorov09.telegram.bot.data.model.Cd2bError
import ru.idfedorov09.telegram.bot.data.model.ProfileResponse
import ru.idfedorov09.telegram.bot.data.model.ReleaseHistory
import ru.idfedorov09.telegram.bot.repo.ReleaseHistoryRepository

/**
 * Сервис для управления релизами
 * RefProfile - опорный профиль
 */
@Component
class ReleaseService(
    private val redisService: RedisService,
    private val cd2bService: Cd2bService,
    private val releaseHistoryRepository: ReleaseHistoryRepository,
) {

    fun getRefProfile(): ProfileResponse? {
        val profileResponse = checkProfileAvailable()
        profileResponse ?: removeRefProfile()
        return profileResponse
    }

    /**
     * Проверяет профиль на доступность.
     * Если доступен - то возвращает это профиль, если нет - то null
     */
    private fun checkProfileAvailable(): ProfileResponse? {
        val profileName = redisService.getSafe(REDIS_REALISE_REFPROFILE_NAME) ?: return null
        val errorStorage = mutableListOf<Cd2bError>()
        val profileResponse = cd2bService.checkProfile(profileName, errorStorage)

        // TODO: а что если отвалится из-за допустим таймаута (большая нагрузка)? это же неправдой будет
        if (errorStorage.any { it.statusCode != 200 }) {
            return null
        }
        return profileResponse
    }

    fun removeRefProfile() {
        val profileName = redisService.getSafe(REDIS_REALISE_REFPROFILE_NAME)
        redisService.del(REDIS_REALISE_REFPROFILE_NAME)
        profileName ?: return
        releaseHistoryRepository.removeWithProfileName(profileName)
    }

    fun newRefProfile(profileName: ProfileResponse) {
        removeRefProfile()
        redisService.setValue(REDIS_REALISE_REFPROFILE_NAME, profileName.name)
        releaseHistoryRepository.save(
            ReleaseHistory(
                profileName = profileName.name,
                stage = ReleaseStages.ABS_EMPTY,
                isFinished = false,
                commitHash = profileName.lastCommit,
                // TODO: текущая дата
            ),
        )
    }
}
