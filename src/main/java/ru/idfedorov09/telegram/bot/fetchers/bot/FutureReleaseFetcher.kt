package ru.idfedorov09.telegram.bot.fetchers.bot

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import ru.idfedorov09.telegram.bot.data.enums.TextCommands
import ru.idfedorov09.telegram.bot.data.model.CallbackData
import ru.idfedorov09.telegram.bot.data.model.Cd2bError
import ru.idfedorov09.telegram.bot.data.model.ProfileResponse
import ru.idfedorov09.telegram.bot.data.model.UserActualizedInfo
import ru.idfedorov09.telegram.bot.executor.Executor
import ru.idfedorov09.telegram.bot.repo.CallbackDataRepository
import ru.idfedorov09.telegram.bot.service.Cd2bService
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
            }
        }
    }
    private fun handleText(params: Params) {
        when {
            params.text == TextCommands.FEATURE_REALISE.commandText -> {
                val refProfileExist = checkSettings()
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
    private fun checkSettings() = releaseService.getRefProfile()?.let { true } ?: false

    /**
     * Метод который вызывается в случае если нет профилей для раскатки релизов
     */
    private fun emptySettings(params: Params) {
        val callbackBack = callbackDataRepository.save(
            CallbackData(
                callbackData = "fr_set_profile|||",
            ),
        )

        val button = InlineKeyboardButton().also {
            it.text = "\uD83D\uDD29 Выбрать"
            it.callbackData = callbackBack.id.toString()
        }

        val sentMsg = bot.execute(
            SendMessage().also {
                it.chatId = params.userActualizedInfo.tui
                it.text = "⚠\uFE0F Не найден опорный профиль. Выбери его"
                it.replyMarkup = createKeyboard(listOf(listOf(button)))
            },
        )

        callbackDataRepository.save(
            callbackBack.copy(
                messageId = sentMsg.messageId.toString(),
            ),
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

        releaseService.newRefProfile(profile)

        // TODO: добавить кнопку "К раскатке"
        bot.execute(
            EditMessageText().also {
                it.chatId = params.userActualizedInfo.tui
                it.messageId = callbackData.messageId?.toInt()
                it.text = "\uD83D\uDCB9 Профиль `$profileName` успешно установлен в качестве опорного!"
                it.parseMode = ParseMode.MARKDOWN
            },
        )
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
        val profiles = cd2bService.getAllProfiles(errorStorage) ?: run {
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

        val chunkedProfiles = profiles
            .chunked(3)
            .map { it.map { it.mapProfileToButton(messageId, callbackKeyboardStorage) } }
            .map { it.toMutableList() }.toMutableList()

        if (chunkedProfiles.size > 1 && chunkedProfiles.last().size == 1) {
            val last = chunkedProfiles[chunkedProfiles.size - 2].removeLast()
            chunkedProfiles.last().add(0, last)
        }

        if (chunkedProfiles.size == 1 && chunkedProfiles[0].size == 3) {
            val last = chunkedProfiles[0].removeLast()
            chunkedProfiles.add(mutableListOf(last))
        }

        callbackKeyboardStorage.keyboard = chunkedProfiles

        // TODO: случай когда профилей нет?
        bot.execute(
            EditMessageText().also {
                it.chatId = chatId
                it.messageId = messageId?.toInt()
                it.text = "\uD83D\uDC40 Выбери опорный профиль:"
                it.replyMarkup = createKeyboard(chunkedProfiles)
            },
        )
    }

    private fun ProfileResponse.mapProfileToButton(
        messageId: String?,
        callbackKeyboardStorage: ManageProfilesFetcher.CallbackKeyboardStorage,
    ) = InlineKeyboardButton()
        .also { button ->
            val callback = callbackDataRepository.save(
                CallbackData(
                    messageId = messageId,
                    callbackData = "#select_refer_profile|${this.name}",
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
