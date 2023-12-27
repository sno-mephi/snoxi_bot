package ru.idfedorov09.telegram.bot.fetchers.bot

import kotlinx.coroutines.delay
import org.apache.commons.lang3.BooleanUtils.xor
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import ru.idfedorov09.telegram.bot.data.GlobalConstants
import ru.idfedorov09.telegram.bot.data.GlobalConstants.BASE_DEVELOPERS_CHAT_ID
import ru.idfedorov09.telegram.bot.data.GlobalConstants.MIN_APPROVES_COUNT
import ru.idfedorov09.telegram.bot.data.GlobalConstants.RR_APPROVES_COUNT
import ru.idfedorov09.telegram.bot.data.GlobalConstants.RR_NEW_VERSION
import ru.idfedorov09.telegram.bot.data.GlobalConstants.RR_PROFILE1
import ru.idfedorov09.telegram.bot.data.GlobalConstants.RR_PROFILE2
import ru.idfedorov09.telegram.bot.data.GlobalConstants.RR_REALISE_STAGE
import ru.idfedorov09.telegram.bot.data.GlobalConstants.RR_TEST_PROFILE
import ru.idfedorov09.telegram.bot.data.enums.ReleaseStages
import ru.idfedorov09.telegram.bot.data.enums.TextCommands
import ru.idfedorov09.telegram.bot.data.model.*
import ru.idfedorov09.telegram.bot.executor.Executor
import ru.idfedorov09.telegram.bot.repo.CallbackDataRepository
import ru.idfedorov09.telegram.bot.repo.SnoxiUserRepository
import ru.idfedorov09.telegram.bot.service.Cd2bService
import ru.idfedorov09.telegram.bot.service.RedisService
import ru.idfedorov09.telegram.bot.service.ReleaseService
import ru.idfedorov09.telegram.bot.service.RouterService
import ru.idfedorov09.telegram.bot.util.CoroutineManager
import ru.idfedorov09.telegram.bot.util.MessageUtils.markdownFormat
import ru.idfedorov09.telegram.bot.util.MessageUtils.shortMessage
import ru.idfedorov09.telegram.bot.util.ReleasesPropertiesStorage
import ru.idfedorov09.telegram.bot.util.UpdatesUtil
import ru.mephi.sno.libs.flow.belly.InjectData
import ru.mephi.sno.libs.flow.fetcher.GeneralFetcher

/**
 * Фетчер для управления раскатками релизов
 */
@Component
class FutureReleaseFetcher(
    private val callbackDataRepository: CallbackDataRepository,
    private val updatesUtil: UpdatesUtil,
    private val releaseService: ReleaseService,
    private val cd2bService: Cd2bService,
    private val bot: Executor,
    private val redisService: RedisService,
    private val releasesPropertiesStorage: ReleasesPropertiesStorage,
    private val snoxiUserRepository: SnoxiUserRepository,
    private val coroutineManager: CoroutineManager,
    private val routerService: RouterService,
) : GeneralFetcher() {

    @InjectData
    fun doFetch(
        update: Update,
        userActualizedInfo: UserActualizedInfo,
    ) {
        val text = runCatching { update.message.text }.getOrNull()

        val params = Params(
            update = update,
            text = text,
            userActualizedInfo = userActualizedInfo,
        )

        when {
            update.hasCallbackQuery() -> handleButtons(params)
            update.hasMessage() && update.message.hasText() -> handleText(params)
        }
    }

    private fun handleButtons(params: Params) {
        val callbackData = callbackDataRepository.findById(
            params.update.callbackQuery.data.toLong(),
        ).get()

        callbackData.callbackData?.apply {
            when {
                startsWith("fr_set_profile") -> buildChooseConsole(params, callbackData)
                startsWith("#select_refer_profile") -> chooseReferProfile(params, callbackData)
                startsWith("#back_empty_select") -> emptySettings(params, callbackData.messageId)
                startsWith("#future_release_button") -> futureRelease(params, callbackData.messageId)
                startsWith("#start_new_release") -> startNewRelease(params, callbackData)
                startsWith("#cancel_last_release") -> TODO("откат предыдущего релиза с прода")
                startsWith("#new_release_ok") -> testingIsOkVote(params, callbackData)
                startsWith("#new_release_fail") -> testingFailureVote(params, callbackData)
            }
        }
    }
    private fun handleText(params: Params) {
        when {
            params.text == TextCommands.FEATURE_REALISE.commandText -> {
                val refProfileExist = isValidSettings()
                if (refProfileExist) {
                    futureRelease(params)
                } else {
                    emptySettings(params)
                }
            }
        }
    }

    @Synchronized
    private fun voteUp() {
        val currentVote = redisService.getSafe(RR_APPROVES_COUNT)?.toLong() ?: 0
        redisService.setValue(RR_APPROVES_COUNT, currentVote.inc().toString())
    }

    private fun testingFailureVote(params: Params, callbackData: CallbackData) {
        val version = callbackData.callbackData?.split("|")?.last()
        val currentNewVersion = redisService.getSafe(RR_NEW_VERSION)

        if (version != currentNewVersion) {
            bot.execute(
                EditMessageText().also {
                    it.chatId = params.userActualizedInfo.tui
                    it.messageId = callbackData.messageId?.toInt()
                    it.text = "_Данный релиз уже выкатили или отменили_"
                    it.parseMode = ParseMode.MARKDOWN
                },
            )
            return
        }

        redisService.del(RR_NEW_VERSION)

        bot.execute(
            EditMessageText().also {
                it.chatId = params.userActualizedInfo.tui
                it.messageId = callbackData.messageId?.toInt()
                it.text = "\uD83D\uDE3F Эхх.. Обязательно сообщи причину в рабочий чат! А я уже отменил выкатку " +
                    "(заметь, тестового бота я не отключал)"
            },
        )
    }

    private fun testingIsOkVote(params: Params, callbackData: CallbackData) {
        val version = callbackData.callbackData?.split("|")?.last()
        val currentNewVersion = redisService.getSafe(RR_NEW_VERSION)

        if (version != currentNewVersion) {
            bot.execute(
                EditMessageText().also {
                    it.chatId = params.userActualizedInfo.tui
                    it.messageId = callbackData.messageId?.toInt()
                    it.text = "_Данный релиз уже выкатили или отменили_"
                    it.parseMode = ParseMode.MARKDOWN
                },
            )
            return
        }

        bot.execute(
            EditMessageText().also {
                it.chatId = params.userActualizedInfo.tui
                it.messageId = callbackData.messageId?.toInt()
                it.text = "Учитываю твой голос..."
            },
        )

        voteUp()
        val currentVote = redisService.getSafe(RR_APPROVES_COUNT)?.toLong() ?: 0
        val remains = Math.max(MIN_APPROVES_COUNT - currentVote, 0L)

        val text = "\uD83D\uDC4C Спасибо, твой голос учтен! " +
            if (remains != 0L) {
                "Остается $remains голосов"
            } else {
                "Сейчас начну выкатку в прод \uD83E\uDD73"
            }

        bot.execute(
            EditMessageText().also {
                it.chatId = params.userActualizedInfo.tui
                it.messageId = callbackData.messageId?.toInt()
                it.text = text
            },
        )

        if (remains == 0L) {
            coroutineManager.doAsync { roadToProduction() }
        }
    }

    private suspend fun roadToProduction(
        pauseBeforeToggleMainProfile: Long = 15000,
    ) {
        val isFirst = routerService.isFirstActive
        val newProfileKey = if (isFirst) RR_PROFILE2 else RR_PROFILE1
        val propertiesBase = if (isFirst) releasesPropertiesStorage.prod2 else releasesPropertiesStorage.prod1

        val newProfileName = redisService.getSafe(newProfileKey) ?: return // TODO: ошибка
        val actualProfile = changeCoreSettings(
            newProfileName,
            isTesting = false,
            propertiesBase,
        )
        cd2bService.setPort(newProfileName, propertiesBase.port.toString())
        cd2bService.rerunProfile(newProfileName, shouldRebuild = true)
        delay(pauseBeforeToggleMainProfile)
        routerService.isFirstActive = isFirst xor true

        bot.execute(
            SendMessage().also {
                it.chatId = BASE_DEVELOPERS_CHAT_ID
                it.text = "\uD83C\uDF89 Мы выкатили новую версию `${actualProfile?.lastCommit}` бота " +
                    "@${propertiesBase.name.markdownFormat()} в прод!"
                it.parseMode = ParseMode.MARKDOWN
            },
        )
    }

    /**
     * Меняет настройки перед запуском; возвращает последний актуальный профиль
     */
    private fun changeCoreSettings(
        profileName: String,
        isTesting: Boolean,
        profilePropertiesBase: ProfilePropertiesBase,
    ): ProfileResponse? {
        val propertiesMap = mapOf(
            "telegram.bot.token" to profilePropertiesBase.token,
            "telegram.bot.name" to profilePropertiesBase.name,
            "telegram.bot.interaction-method" to (if (isTesting) "polling" else "webhook"),
            "server.address" to "0.0.0.0",
            "spring.redis.host" to profilePropertiesBase.redisHost,
            "spring.redis.port" to profilePropertiesBase.redisPort.toString(),
            "spring.redis.password" to profilePropertiesBase.redisPassword,
            "spring.datasource.url" to profilePropertiesBase.postgresUrl,
            "spring.datasource.username" to profilePropertiesBase.postgresUsername,
            "spring.datasource.password" to profilePropertiesBase.postgresPassword,
            "spring.jpa.hibernate.ddl-auto" to "update", // TODO: ВАЖНО! А ЧТО ЕСЛИ С ЭТИМ ПРИДЕТСЯ ЧТО-ТО ДЕЛАТЬ??
        )

        return propertiesMap.map {
            cd2bService.changePropertiesField(
                profileName,
                it.key,
                it.value,
            )
        }.lastOrNull()
    }
    private fun startNewRelease(params: Params, callbackData: CallbackData) {
        val testProfileName = redisService.getSafe(RR_TEST_PROFILE)
            ?: return emptySettings(params, callbackData.messageId)

        bot.execute(
            EditMessageText().also {
                it.chatId = params.userActualizedInfo.tui
                it.messageId = callbackData.messageId?.toInt()
                it.text = "Накатываю конфиги..."
            },
        )
        val actualProfile = changeCoreSettings(
            testProfileName,
            isTesting = true,
            releasesPropertiesStorage.test,
        ) ?: return // TODO: ошибка

        redisService.setValue(RR_NEW_VERSION, actualProfile.lastCommit)

        bot.execute(
            EditMessageText().also {
                it.chatId = params.userActualizedInfo.tui
                it.messageId = callbackData.messageId?.toInt()
                it.text = "Я начал развертывание тестового бота. После $MIN_APPROVES_COUNT аппрувов " +
                    "раскатка перейдет к следующему этапу - в прод. \nО запуске ты узнаешь в отдельном сообщении."
            },
        )
        coroutineManager.doAsync { runTestProfile(testProfileName, actualProfile) }
    }

    private suspend fun runTestProfile(
        testProfileName: String,
        actualProfile: ProfileResponse,
        messageSendDelay: Long = 5000,
    ) {
        cd2bService.rerunProfile(profileName = testProfileName, shouldRebuild = true)

        snoxiUserRepository.findAll().forEach { snoxiUser ->
            snoxiUser.tui ?: return@forEach

            // TODO: поддержать версию???
            val okCallback = callbackDataRepository.save(
                CallbackData(
                    callbackData = "#new_release_ok|${actualProfile.lastCommit}",
                ),
            )
            val failCallback = callbackDataRepository.save(
                CallbackData(
                    callbackData = "#new_release_fail|${actualProfile.lastCommit}",
                ),
            )

            val okButton = InlineKeyboardButton().also {
                it.text = "\uD83D\uDC4D"
                it.callbackData = okCallback.id.toString()
            }

            val failButton = InlineKeyboardButton().also {
                it.text = "\uD83D\uDC4E"
                it.callbackData = failCallback.id.toString()
            }

            runCatching {
                val sentMessage = bot.execute(
                    SendMessage().also {
                        it.chatId = snoxiUser.tui
                        it.text = "\uD83D\uDE80 В тестинг выкатилась новая версия бота. " +
                            "Для тестирования напиши в @${releasesPropertiesStorage.test.name}. " +
                            "Последние изменения можно найти в ${actualProfile.repoUri}.\n\n" +
                            "⚠\uFE0F После тестирования обязательно оставь свой голос. " +
                            "От этого будет зависеть, поедет ли новая версия в прод."
                        it.replyMarkup = createKeyboard(listOf(listOf(okButton, failButton)))
                    },
                )
                callbackDataRepository.save(okCallback.copy(messageId = sentMessage.messageId.toString()))
                callbackDataRepository.save(failCallback.copy(messageId = sentMessage.messageId.toString()))
                delay(messageSendDelay)
            }
        }
    }

    private fun futureRelease(
        params: Params,
        msgId: String? = null,
    ) {
        val currentStage = ReleaseStages.valueOf(redisService.getSafe(RR_REALISE_STAGE) ?: "ABS_EMPTY")
        val text = "Текущий статус: _${currentStage.description}_"

        val newReleaseCallback = callbackDataRepository.save(
            CallbackData(
                callbackData = "#start_new_release",
            ),
        )
        val cancelLastReleaseCallback = callbackDataRepository.save(
            CallbackData(
                callbackData = "#cancel_last_release",
            ),
        )

        val newReleaseButton = InlineKeyboardButton().also {
            it.text = "\uD83D\uDD39 Новый релиз"
            it.callbackData = newReleaseCallback.id.toString()
        }

        val cancelLastReleaseButton = InlineKeyboardButton().also {
            it.text = "\uD83D\uDD34 Откатить прошлый релиз"
            it.callbackData = cancelLastReleaseCallback.id.toString()
        }

        val messageId = msgId?.also {
            bot.execute(
                EditMessageText().also {
                    it.messageId = msgId.toInt()
                    it.chatId = params.userActualizedInfo.tui
                    it.text = text
                    it.replyMarkup = createKeyboard(listOf(listOf(newReleaseButton), listOf(cancelLastReleaseButton)))
                    it.parseMode = ParseMode.MARKDOWN
                },
            )
        } ?: bot.execute(
            SendMessage().also {
                it.chatId = params.userActualizedInfo.tui
                it.text = text
                it.replyMarkup = createKeyboard(listOf(listOf(newReleaseButton), listOf(cancelLastReleaseButton)))
                it.parseMode = ParseMode.MARKDOWN
            },
        ).messageId

        callbackDataRepository.save(newReleaseCallback.copy(messageId = messageId.toString()))
        callbackDataRepository.save(cancelLastReleaseCallback.copy(messageId = messageId.toString()))
    }

    /**
     * Проверка целостности настроек; если ошибка при проверке целостности - сбить все нафиг
     */
    private fun isValidSettings() = releaseService.getRefProfiles().isNotEmpty()

    private fun cbDataFor(
        profileKey: String,
        data: String,
        messageId: String?,
    ): CallbackData? {
        if (redisService.getSafe(profileKey) != null) return null
        return callbackDataRepository.save(
            CallbackData(
                callbackData = data,
                messageId = messageId,
            ),
        )
    }

    /**
     * Метод который вызывается в случае если нет профилей для раскатки релизов / они некорректны
     */
    private fun emptySettings(params: Params, lastMessageId: String? = null) {
        val text = if (lastMessageId == null) {
            "⚠\uFE0F Не найдены настройки опорных профилей, либо они слетели. Настрой их!\n\n" +
                "Небольшая подсказка: для раскатки релизов необходимо три опорных профиля.\n" +
                "Один из них -- тестовый, два - продуктовых (прод1 и прод2). " +
                "Тестовый нужен для тестирования на стаффе, " +
                "продуктовые -- для подмен во избежание даунтайма."
        } else {
            "Теперь выбери следующий профиль."
        }

        val messageId = if (lastMessageId == null) {
            bot.execute(
                SendMessage().also {
                    it.chatId = params.userActualizedInfo.tui
                    it.text = text
                },
            ).messageId.toString()
        } else {
            bot.execute(
                EditMessageText().also {
                    it.chatId = params.userActualizedInfo.tui
                    it.messageId = lastMessageId.toInt()
                    it.text = text
                },
            )
            lastMessageId
        }

        val dataFirstProfile = cbDataFor(GlobalConstants.RR_PROFILE1, "fr_set_profile_1", messageId)
        val dataSecondProfile = cbDataFor(GlobalConstants.RR_PROFILE2, "fr_set_profile_2", messageId)
        val dataTestProfile = cbDataFor(GlobalConstants.RR_TEST_PROFILE, "fr_set_profile_test", messageId)
        val buttonCallbacks = listOf(dataFirstProfile, dataSecondProfile, dataTestProfile)

        val buttons = listOf(
            buttonCallbacks.filterNotNull().map { cb ->
                InlineKeyboardButton().also {
                    it.text = when (cb.callbackData) {
                        "fr_set_profile_1" -> "Выбрать Прод1"
                        "fr_set_profile_2" -> "Выбрать Прод2"
                        "fr_set_profile_test" -> "Выбрать Тестовый"
                        else -> "Это баг такой лол"
                    }
                    it.callbackData = cb.id.toString()
                }
            },
        )

        bot.execute(
            EditMessageText().also {
                it.chatId = params.userActualizedInfo.tui
                it.messageId = messageId.toInt()
                it.text = text
                it.replyMarkup = createKeyboard(buttons)
            },
        )
    }

    private fun chooseReferProfile(params: Params, callbackData: CallbackData) {
        val profileName = callbackData.callbackData?.split("|")?.last() ?: return
        val profile = cd2bService.checkProfile(profileName) ?: run {
            bot.execute(
                EditMessageText().also {
                    it.chatId = params.userActualizedInfo.tui
                    it.messageId = callbackData.messageId?.toInt()
                    it.text = "Данный профиль недоступен."
                },
            )
            return
        }

        val isEnd = when (callbackData.callbackData.split("|")[1]) {
            "fr_set_profile_1" -> releaseService.newFirstProfile(profile)
            "fr_set_profile_2" -> releaseService.newSecondProfile(profile)
            "fr_set_profile_test" -> releaseService.newTestProfile(profile)
            else -> throw Exception("Ошибка при обработке нажатия на кнопку с выбором профиля")
        }

        if (isEnd) {
            val futureReleaseCallback = callbackDataRepository.save(
                CallbackData(
                    messageId = callbackData.messageId,
                    callbackData = "#future_release_button",
                ),
            )
            val button = InlineKeyboardButton().also {
                it.text = "К раскатке"
                it.callbackData = futureReleaseCallback.id.toString()
            }

            bot.execute(
                EditMessageText().also {
                    it.chatId = params.userActualizedInfo.tui
                    it.messageId = callbackData.messageId?.toInt()
                    it.text = "\uD83D\uDCB9 Профили для раскатки успешно настроены"
                    it.replyMarkup = createKeyboard(listOf(listOf(button)))
                },
            )

            redisService.setValue(RR_REALISE_STAGE, ReleaseStages.ABS_EMPTY())
        } else {
            emptySettings(params, callbackData.messageId)
        }
    }
    private fun buildChooseConsole(params: Params, callbackData: CallbackData) {
        val chatId = params.userActualizedInfo.tui
        val messageId = callbackData.messageId

        bot.execute(
            EditMessageText().also {
                it.chatId = chatId
                it.messageId = messageId?.toInt()
                it.text = "Собираю информацию о профилях..."
            },
        )
        val errorStorage = mutableListOf<Cd2bError>()
        val filterMap = listOf(RR_PROFILE1, RR_PROFILE2, RR_TEST_PROFILE).mapNotNull { redisService.getSafe(it) }
        val profiles = cd2bService
            .getAllProfiles(errorStorage)
            ?.filter { it.name !in filterMap }
            ?: run {
                val error = errorStorage.lastOrNull()
                bot.execute(
                    EditMessageText().also {
                        it.chatId = chatId
                        it.messageId = messageId?.toInt()

                        // TODO: написать сервис для иницидентов
                        val statusCode = "${error?.statusCode}".markdownFormat()
                        val desc = "${error?.statusDescription}".markdownFormat()
                        val trace = "${error?.stackTrace}".markdownFormat()

                        val text = "❗\uFE0FОбнаружен инцидент\\. Сервис cd2b вернул код `$statusCode`\\.\n" +
                            "Описание: `$desc`\n" +
                            "Трасса:\n```\n$trace"
                                .shortMessage(4096 - 3)
                                .plus("```")

                        it.text = text
                        it.parseMode = ParseMode.MARKDOWNV2
                    },
                )
                return
            }

        val callbackKeyboardStorage = ManageProfilesFetcher.CallbackKeyboardStorage()
        val additionalData = callbackData.callbackData ?: "error"

        val chunkedProfiles = profiles
            .chunked(3)
            .map { it.map { it.mapProfileToButton(messageId, callbackKeyboardStorage, additionalData) } }
            .map { it.toMutableList() }.toMutableList()

        if (chunkedProfiles.size > 1 && chunkedProfiles.last().size == 1) {
            val last = chunkedProfiles[chunkedProfiles.size - 2].removeLast()
            chunkedProfiles.last().add(0, last)
        }

        if (chunkedProfiles.size == 1 && chunkedProfiles[0].size == 3) {
            val last = chunkedProfiles[0].removeLast()
            chunkedProfiles.add(mutableListOf(last))
        }

        val backCallbackData = callbackDataRepository.save(
            CallbackData(
                messageId = messageId,
                callbackData = "#back_empty_select",
            ),
        )
        val backButton = InlineKeyboardButton().also {
            it.text = "Назад"
            it.callbackData = backCallbackData.id.toString()
        }

        chunkedProfiles.add(mutableListOf(backButton))

        callbackKeyboardStorage.keyboard = chunkedProfiles

        // TODO: случай когда профилей нет?
        // TODO: кнопка НАЗАД
        bot.execute(
            EditMessageText().also {
                it.chatId = chatId
                it.messageId = messageId?.toInt()
                it.text = "\uD83D\uDC40 Выбери профиль:"
                it.replyMarkup = createKeyboard(chunkedProfiles)
            },
        )
    }

    private fun ProfileResponse.mapProfileToButton(
        messageId: String?,
        callbackKeyboardStorage: ManageProfilesFetcher.CallbackKeyboardStorage,
        additionalData: String,
    ) = InlineKeyboardButton()
        .also { button ->
            val callback = callbackDataRepository.save(
                CallbackData(
                    messageId = messageId,
                    callbackData = "#select_refer_profile|$additionalData|${this.name}",
                ),
            )
            callbackKeyboardStorage.addCallback(callback)
            button.callbackData = callback.id.toString()
            button.text = this.name
        }

    private fun createKeyboard(keyboard: List<List<InlineKeyboardButton>>) =
        InlineKeyboardMarkup().also { it.keyboard = keyboard }

    private data class Params(
        val update: Update,
        val text: String?,
        var userActualizedInfo: UserActualizedInfo,
    )
}
