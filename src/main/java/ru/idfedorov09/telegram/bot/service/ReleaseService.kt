package ru.idfedorov09.telegram.bot.service

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import ru.idfedorov09.telegram.bot.data.GlobalConstants.RR_PROFILE1
import ru.idfedorov09.telegram.bot.data.GlobalConstants.RR_PROFILE2
import ru.idfedorov09.telegram.bot.data.GlobalConstants.RR_TEST_PROFILE
import ru.idfedorov09.telegram.bot.data.enums.ReleaseStages
import ru.idfedorov09.telegram.bot.data.model.Cd2bError
import ru.idfedorov09.telegram.bot.data.model.ProfileResponse
import ru.idfedorov09.telegram.bot.data.model.ReleaseHistory
import ru.idfedorov09.telegram.bot.repo.ReleaseHistoryRepository
import java.time.LocalDateTime
import java.time.ZoneId

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

    private val profileKeys = listOf(
        RR_PROFILE1,
        RR_PROFILE2,
        RR_TEST_PROFILE,
    )

    /**
     * Вытаскивает все профили
     */
    fun getRefProfiles(): List<ProfileResponse> {
        val profiles = profileKeys.map { getRefProfile(it) }
        if (profiles.any { it == null }) {
            releaseHistoryRepository.deleteAll()
            return listOf()
        }
        return profiles.filterNotNull()
    }

    private fun getRefProfile(redisProfileKey: String): ProfileResponse? {
        val profileResponse = checkProfileAvailability(redisProfileKey)
        profileResponse ?: removeRefProfile(redisProfileKey)
        return profileResponse
    }

    /**
     * Проверяет профиль на доступность. Его имя - значение ключа redisProfileKey из редиса
     * Если доступен - то возвращает это профиль, если нет - то null
     */
    private fun checkProfileAvailability(redisProfileKey: String): ProfileResponse? {
        val profileName = redisService.getSafe(redisProfileKey) ?: return null
        val errorStorage = mutableListOf<Cd2bError>()
        val profileResponse = cd2bService.checkProfile(profileName, errorStorage)

        // TODO: а что если отвалится из-за допустим таймаута (большая нагрузка)? это же неправдой будет
        if (errorStorage.any { it.statusCode != 200 }) {
            return null
        }
        return profileResponse
    }

    private fun removeRefProfile(redisProfileKey: String) = redisService.del(redisProfileKey)

    /**
     * Если все профили выгружены, то записывает это в бдшку
     */
    private fun tryApplyAllChanges(): Boolean {
        val profileNames = profileKeys.associateWith { redisService.getSafe(it) }
        if (profileNames.any { it.value == null }) return false
        releaseHistoryRepository.save(
            ReleaseHistory(
                firstProfileName = profileNames[RR_PROFILE1],
                secondProfileName = profileNames[RR_PROFILE1],
                testProfileName = profileNames[RR_TEST_PROFILE],
                stage = ReleaseStages.ABS_EMPTY,
                isFinished = false,
                commitHash = null,
                eventDescription = "Изменены настройки раскатки.",
                time = LocalDateTime.now(ZoneId.of("Europe/Moscow")),
            ),
        )
        return true
    }

    private fun newRefProfile(
        redisProfileKey: String,
        profile: ProfileResponse,
    ): Boolean {
        removeRefProfile(redisProfileKey)
        redisService.setValue(redisProfileKey, profile.name)
        return tryApplyAllChanges()
    }

    fun newTestProfile(profile: ProfileResponse) = newRefProfile(RR_TEST_PROFILE, profile)
    fun newFirstProfile(profile: ProfileResponse) = newRefProfile(RR_PROFILE1, profile)
    fun newSecondProfile(profile: ProfileResponse) = newRefProfile(RR_PROFILE2, profile)
}
