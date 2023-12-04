package ru.idfedorov09.telegram.bot.fetchers.bot

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import ru.idfedorov09.telegram.bot.data.enums.TextCommands
import ru.idfedorov09.telegram.bot.data.enums.UserActionType.TYPING_PORT
import ru.idfedorov09.telegram.bot.data.model.CallbackData
import ru.idfedorov09.telegram.bot.data.model.Cd2bError
import ru.idfedorov09.telegram.bot.data.model.ProfileResponse
import ru.idfedorov09.telegram.bot.data.model.UserActualizedInfo
import ru.idfedorov09.telegram.bot.executor.Executor
import ru.idfedorov09.telegram.bot.repo.CallbackDataRepository
import ru.idfedorov09.telegram.bot.service.Cd2bService
import ru.idfedorov09.telegram.bot.util.MessageUtils.markdownFormat
import ru.idfedorov09.telegram.bot.util.MessageUtils.shortMessage
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
        val text = updatesUtil.getText(update) ?: ""

        when {
            update.hasCallbackQuery() -> handleButtons(update, userActualizedInfo)
            update.hasMessage() && update.message.hasText() -> handleText(update, userActualizedInfo, text)
        }
    }

    /**
     * Выбирает нужный метод для обработки команд / текста
     */
    private fun handleText(
        update: Update,
        userActualizedInfo: UserActualizedInfo,
        text: String,
    ) {
        when {
            userActualizedInfo.currentActionType == TYPING_PORT &&
                !TextCommands.isTextCommand(text) -> setPort(update, userActualizedInfo, text)
            text == TextCommands.MANAGE_PROFILES.commandText -> buildConsole(userActualizedInfo.tui)
        }
    }

    /**
     * Выбирает нужный метод для обработки нажатия на кнопку
     */
    private fun handleButtons(
        update: Update,
        userActualizedInfo: UserActualizedInfo,
    ) {
        val callbackData = callbackDataRepository.findById(
            update.callbackQuery.data.toLong(),
        ).get()

        callbackData.callbackData?.apply {
            when {
                startsWith("new profile") -> {
                    resetUserData(userActualizedInfo, removeConsole = false)
                    newProfile()
                }
                startsWith("#profile") -> {
                    resetUserData(userActualizedInfo, removeConsole = false)
                    selectProfile(update, userActualizedInfo, callbackData)
                }
                startsWith("#back_to_list") -> {
                    resetUserData(userActualizedInfo, removeConsole = false)
                    // val messageId = callbackDataRepository.findById(update.callbackQuery.data.toLong()).get().messageId
                    buildConsole(
                        userActualizedInfo.tui,
                        update.callbackQuery.message.messageId.toString(),
                    )
                }
                startsWith("#set_port") -> {
                    resetUserData(userActualizedInfo, removeConsole = false)
                    clickSetPort(update, userActualizedInfo, callbackData)
                }
            }
        }
    }

    private fun clickSetPort(
        update: Update,
        userActualizedInfo: UserActualizedInfo,
        callbackData: CallbackData,
    ) {
        val profileName = callbackData.callbackData?.split("|")?.last() ?: return
        val messageId = update.callbackQuery.message.messageId
        val profileResponse = cd2bService.checkProfile(profileName) ?: return

        val name = profileResponse.name.markdownFormat()

        val callbackBack = callbackDataRepository.save(
            CallbackData(
                messageId = messageId.toString(),
                callbackData = "#profile|$profileName",
            ),
        )

        val keyboard = createKeyboard(
            listOf(
                listOf(
                    InlineKeyboardButton().also {
                        it.text = "Отмена"
                        it.callbackData = callbackBack.id.toString()
                    },
                ),
            ),
        )

        userActualizedInfo.currentActionType = TYPING_PORT
        userActualizedInfo.data = "$profileName|$messageId"

        bot.execute(
            EditMessageText().also {
                it.chatId = userActualizedInfo.tui
                it.messageId = messageId
                it.text = "_Вы редактируете порт профиля_ `$name`.\n" +
                    "Текущий порт: `${profileResponse.port}`.\n\n" +
                    "В следующем сообщении отправь порт, который ты хочешь установить."
                it.parseMode = ParseMode.MARKDOWN
                it.replyMarkup = keyboard
            },
        )
    }

    private fun resetUserData(
        userActualizedInfo: UserActualizedInfo,
        removeConsole: Boolean = true,
    ) {
        userActualizedInfo.currentActionType = null
        val data = userActualizedInfo.data ?: return
        userActualizedInfo.data = null

        if (!removeConsole) return
        runCatching {
            val messageId = data.split("|")[1]
            bot.execute(
                DeleteMessage().also {
                    it.chatId = userActualizedInfo.tui
                    it.messageId = messageId.toInt()
                },
            )
        }
    }

    private fun setPort(
        update: Update,
        userActualizedInfo: UserActualizedInfo,
        text: String,
    ) {
        val data = userActualizedInfo.data ?: return

        val profileName = data.split("|")[0]
        val messageId = data.split("|")[1]

        bot.execute(
            DeleteMessage().also {
                it.chatId = userActualizedInfo.tui
                it.messageId = update.message.messageId
            },
        )

        bot.execute(
            EditMessageText().also {
                it.chatId = userActualizedInfo.tui
                it.messageId = messageId.toInt()
                it.text = "Пробую обновить порт..."
            },
        )

        val errorStorage = mutableListOf<Cd2bError>()
        cd2bService.setPort(
            profileName,
            text,
            errorStorage,
        )

        val callbackBack = callbackDataRepository.save(
            CallbackData(
                messageId = messageId,
                callbackData = "#profile|$profileName",
            ),
        )

        val keyboard = createKeyboard(
            listOf(
                listOf(
                    InlineKeyboardButton().also {
                        it.text = "Отмена"
                        it.callbackData = callbackBack.id.toString()
                    },
                ),
            ),
        )

        if (errorStorage.any { it.statusCode == 400 }) {
            bot.execute(
                EditMessageText().also {
                    it.chatId = userActualizedInfo.tui
                    it.messageId = messageId.toInt()
                    it.text = "❌ Некорректный порт.\n Порт должен быть целым числом из отрезка `[1; 65535]`. " +
                        "Попробуй ввести порт еще раз."
                    it.replyMarkup = keyboard
                    it.parseMode = ParseMode.MARKDOWN
                },
            )
        } else {
            showProfileInfo(
                profileName = profileName,
                messageId = messageId,
                chatId = userActualizedInfo.tui,
            )

            userActualizedInfo.currentActionType = null
            userActualizedInfo.data = null
        }
    }

    private fun newProfile() {
        TODO()
    }

    private fun selectProfile(
        update: Update,
        userActualizedInfo: UserActualizedInfo,
        callbackData: CallbackData,
    ) {
        val profileName = callbackData.callbackData?.split("|")?.last() ?: return
        val messageId = update.callbackQuery.message.messageId
        showProfileInfo(profileName, messageId.toString(), userActualizedInfo.tui)
    }

    /**
     * Редачит сообщение с messageId в чате chatId и показывает в нем инфу по профилю profileName
     */
    private fun showProfileInfo(
        profileName: String,
        messageId: String,
        chatId: String,
    ) {
        val profileResponse = cd2bService.checkProfile(profileName) ?: return

        val name = profileResponse.name.markdownFormat()
        val repoUrl = profileResponse.repoUri
        val imageName = profileResponse.imageName.markdownFormat()

        val isRunningText = if (profileResponse.isRunning) {
            "\uD83D\uDFE2Запущен"
        } else {
            "\uD83D\uDD34Не запущен"
        }.markdownFormat()

        val text = "_Информация о профиле_ `$name`:\n\n" +
            "✏\uFE0FИмя приложения: [${profileResponse.repoName}]($repoUrl)\n" +
            "$isRunningText\n" +
            "\uD83D\uDCF6Активный порт: ${profileResponse.port}\n" +
            "\uD83D\uDCE6Имя Docker-image: $imageName"

        val callbackBack = callbackDataRepository.save(
            CallbackData(
                messageId = messageId,
                callbackData = "#back_to_list",
            ),
        )
        val callbackPort = callbackDataRepository.save(
            CallbackData(
                messageId = messageId,
                callbackData = "#set_port|$profileName",
            ),
        )

        // TODO: добавить кнопки:
        // - удалить
        // - установить проперти
        // - запустить/перезапустить

        val keyboard = createKeyboard(
            listOf(
                listOf(
                    InlineKeyboardButton().also {
                        it.text = "\uD83D\uDCF6Настройка порта"
                        it.callbackData = callbackPort.id.toString()
                    },
                ),
                listOf(
                    InlineKeyboardButton().also {
                        it.text = "◀\uFE0FНазад"
                        it.callbackData = callbackBack.id.toString()
                    },
                ),
            ),
        )

        bot.execute(
            EditMessageText().also {
                it.chatId = chatId
                it.messageId = messageId.toInt()
                it.text = text
                it.replyMarkup = keyboard
                it.parseMode = ParseMode.MARKDOWN
            },
        )
    }

    /**
     * Строит клавиатуру (АКА консоль) с профилями
     * chatId - id чата, в котором строится клавиатура
     * messageId - id сообщения, на котором отобразится консоль
     * desc - текст-описание
     * onFailDesc - текст-описание при некорректной работе cd2b
     */
    private fun buildConsole(
        chatId: String,
        consoleMessageId: String? = null,
        prepareMessage: String = "Собираю информацию о профилях...",
    ): CallbackKeyboardStorage? {
        val messageId = when {
            consoleMessageId != null -> {
                bot.execute(
                    EditMessageText().also {
                        it.chatId = chatId
                        it.messageId = consoleMessageId.toInt()
                        it.text = prepareMessage
                    },
                )
                consoleMessageId
            }
            else -> {
                val sent = bot.execute(
                    SendMessage().also {
                        it.chatId = chatId
                        it.text = prepareMessage
                    },
                )
                sent.messageId.toString()
            }
        }

        val errorStorage = mutableListOf<Cd2bError>()

        val profiles = cd2bService.getAllProfiles(errorStorage) ?: run {
            val error = errorStorage.lastOrNull()
            bot.execute(
                EditMessageText().also {
                    it.chatId = chatId
                    it.messageId = messageId.toInt()

                    // TODO: написать сервис для иницидентов
                    val statusCode = "${error?.statusCode}".markdownFormat()
                    val desc = "${error?.statusDescription}".markdownFormat()
                    val trace = "${error?.stackTrace}".markdownFormat()

                    val text = "❗\uFE0FОбнаружен инцидент\\. Сервис cd2b вернул код `$statusCode`.\n" +
                        "Описание: `$desc`\n" +
                        "Трасса:\n```\n$trace"
                            .shortMessage(4096 - 3)
                            .plus("```")

                    it.text = text
                    it.parseMode = ParseMode.MARKDOWNV2
                },
            )
            return null
        }
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

        bot.execute(
            EditMessageText().also {
                it.chatId = chatId
                it.messageId = messageId.toInt()
                it.text = "\uD83D\uDC40 Выбери профиль:"
                it.replyMarkup = createKeyboard(chunkedProfiles)
            },
        )
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
                    callbackData = "#profile|${this.name}",
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
