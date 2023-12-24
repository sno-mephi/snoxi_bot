package ru.idfedorov09.telegram.bot.service

import org.springframework.stereotype.Component
import ru.idfedorov09.telegram.bot.data.GlobalConstants.REDIS_REALISE_REFPROFILE_NAME
import ru.idfedorov09.telegram.bot.data.model.Cd2bError
import ru.idfedorov09.telegram.bot.data.model.ProfileResponse

/**
 * Сервис для управления релизами
 * RefProfile - опорный профиль
 */
@Component
class ReleaseService(
    private val redisService: RedisService,
    private val cd2bService: Cd2bService,
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
        // TODO: подчистить таблицу по profileName
    }

    fun newRefProfile(profileName: String?) {
        redisService.setValue(REDIS_REALISE_REFPROFILE_NAME, profileName)
        // TODO: назначить опорный профиль
    }
}
