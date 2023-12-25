package ru.idfedorov09.telegram.bot.fetchers.bot

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import ru.idfedorov09.telegram.bot.data.GlobalConstants
import ru.idfedorov09.telegram.bot.data.GlobalConstants.RR_PROFILE1
import ru.idfedorov09.telegram.bot.data.GlobalConstants.RR_PROFILE2
import ru.idfedorov09.telegram.bot.data.GlobalConstants.RR_TEST_PROFILE
import ru.idfedorov09.telegram.bot.data.enums.TextCommands
import ru.idfedorov09.telegram.bot.data.model.CallbackData
import ru.idfedorov09.telegram.bot.data.model.Cd2bError
import ru.idfedorov09.telegram.bot.data.model.ProfileResponse
import ru.idfedorov09.telegram.bot.data.model.UserActualizedInfo
import ru.idfedorov09.telegram.bot.executor.Executor
import ru.idfedorov09.telegram.bot.repo.CallbackDataRepository
import ru.idfedorov09.telegram.bot.service.Cd2bService
import ru.idfedorov09.telegram.bot.service.RedisService
import ru.idfedorov09.telegram.bot.service.ReleaseService
import ru.idfedorov09.telegram.bot.util.MessageUtils.markdownFormat
import ru.idfedorov09.telegram.bot.util.MessageUtils.shortMessage
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
            }
        }
    }
    private fun handleText(params: Params) {
        when {
            params.text == TextCommands.FEATURE_REALISE.commandText -> {
                val refProfileExist = isValidSettings()
                if (refProfileExist) {
                    futureRealize(params)
                } else {
                    emptySettings(params)
                }
            }
        }
    }

    private fun futureRealize(
        params: Params,
        msgId: String? = null,
    ) {
        val text = "empty"
        val messageId = msgId?.also {
            bot.execute(
                EditMessageText().also {
                    it.messageId = msgId.toInt()
                    it.chatId = params.userActualizedInfo.tui
                    it.text = text
                },
            )
        } ?: bot.execute(
            SendMessage().also {
                it.chatId = params.userActualizedInfo.tui
                it.text = text
            },
        )
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

        // TODO: добавить кнопку "К раскатке"
        if (isEnd) {
            bot.execute(
                EditMessageText().also {
                    it.chatId = params.userActualizedInfo.tui
                    it.messageId = callbackData.messageId?.toInt()
                    it.text = "\uD83D\uDCB9 Профили для раскатки успешно настроены!"
                },
            )
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
