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
import ru.idfedorov09.telegram.bot.data.model.ProfileResponse
import ru.idfedorov09.telegram.bot.data.model.UserActualizedInfo
import ru.idfedorov09.telegram.bot.executor.Executor
import ru.idfedorov09.telegram.bot.repo.CallbackDataRepository
import ru.idfedorov09.telegram.bot.service.Cd2bService
import ru.idfedorov09.telegram.bot.util.UpdatesUtil
import ru.mephi.sno.libs.flow.belly.InjectData
import ru.mephi.sno.libs.flow.fetcher.GeneralFetcher

/**
 * Фетчер для управления профилями
 */
@Component
class ManageProfilesFetcher(
    private val callbackDataRepository: CallbackDataRepository,
    private val updatesUtil: UpdatesUtil,
    private val bot: Executor,
    private val cd2bService: Cd2bService,
) : GeneralFetcher() {

    @InjectData
    fun doFetch(
        update: Update,
        userActualizedInfo: UserActualizedInfo,
    ) {
        val text = updatesUtil.getText(update)

        when {
            update.hasCallbackQuery() -> handleButtons(update, userActualizedInfo)
            text == TextCommands.MANAGE_PROFILES.commandText -> startManage(update, userActualizedInfo)
        }
    }

    private fun handleButtons(
        update: Update,
        userActualizedInfo: UserActualizedInfo,
    ) {
        TODO()
    }

    private fun startManage(
        update: Update,
        userActualizedInfo: UserActualizedInfo,
    ) {
        val sentMsg = bot.execute(
            SendMessage().also {
                it.chatId = userActualizedInfo.tui
                it.text = "Собираю информацию о профилях..."
            },
        )
        buildConsole(
            userActualizedInfo.tui,
            sentMsg.messageId.toString(),
        ) ?: bot.execute(
            EditMessageText().also {
                it.chatId = userActualizedInfo.tui
                it.messageId = sentMsg.messageId
                it.text = "❗\uFE0FОбнаружен инцидент: *cd2b* недоступен\\."
                it.parseMode = ParseMode.MARKDOWNV2
            },
        )
    }

    /**
     * Строит клавиатуру (АКА консоль) с профилями
     * chatId - id чата, в котором строится клавиатура
     * messageId - id сообщения, на котором отобразится консоль
     * desc - текст-описание
     */
    private fun buildConsole(
        chatId: String,
        messageId: String?,
        desc: String = "\uD83D\uDC40 Выбери профиль:",
    ): CallbackKeyboardStorage? {
        val profiles = cd2bService.getAllProfiles() ?: return null
        val callbackKeyboardStorage = CallbackKeyboardStorage()

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

        chunkedProfiles.add(
            mutableListOf(
                InlineKeyboardButton().also {
                    val callback = callbackDataRepository.save(
                        CallbackData(
                            messageId = messageId,
                            callbackData = "new profile",
                        ),
                    )
                    it.text = "Создать новый профиль"
                    it.callbackData = callback.id.toString()
                },
            ),
        )

        callbackKeyboardStorage.keyboard = chunkedProfiles

        messageId?.let { id ->
            bot.execute(
                EditMessageText().also {
                    it.chatId = chatId
                    it.messageId = id.toInt()
                    it.text = desc
                    it.replyMarkup = createKeyboard(chunkedProfiles)
                },
            )
        }
        return callbackKeyboardStorage
    }

    private fun ProfileResponse.mapProfileToButton(
        messageId: String?,
        callbackKeyboardStorage: CallbackKeyboardStorage,
    ) = InlineKeyboardButton()
        .also { button ->
            val callback = callbackDataRepository.save(
                CallbackData(
                    messageId = messageId,
                    callbackData = "profile|${this.name}",
                ),
            )
            callbackKeyboardStorage.addCallback(callback)
            button.callbackData = callback.id.toString()
            button.text = this.name
        }

    private fun createKeyboard(keyboard: List<List<InlineKeyboardButton>>) =
        InlineKeyboardMarkup().also { it.keyboard = keyboard }

    private data class CallbackKeyboardStorage(
        private val store: MutableList<CallbackData> = mutableListOf(),
        var keyboard: List<List<InlineKeyboardButton>> = listOf(listOf()),
    ) {
        fun addCallback(callbackData: CallbackData) = store.add(callbackData)

        fun applyMessage(
            messageId: String,
            callbackDataRepository: CallbackDataRepository,
        ) = callbackDataRepository.saveAll(store.map { it.copy(messageId = messageId) })
    }
}
