package ru.idfedorov09.telegram.bot.fetchers.bot

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import ru.idfedorov09.telegram.bot.data.GlobalConstants.MAX_MSG_LENGTH
import ru.idfedorov09.telegram.bot.data.enums.TextCommands
import ru.idfedorov09.telegram.bot.data.enums.UserActionType.*
import ru.idfedorov09.telegram.bot.data.model.*
import ru.idfedorov09.telegram.bot.executor.Executor
import ru.idfedorov09.telegram.bot.repo.CallbackDataRepository
import ru.idfedorov09.telegram.bot.service.Cd2bService
import ru.idfedorov09.telegram.bot.service.RedisService
import ru.idfedorov09.telegram.bot.util.MessageUtils.markdownFormat
import ru.idfedorov09.telegram.bot.util.MessageUtils.shortMessage
import ru.idfedorov09.telegram.bot.util.UpdatesUtil
import ru.mephi.sno.libs.flow.belly.InjectData
import ru.mephi.sno.libs.flow.fetcher.GeneralFetcher
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Фетчер для управления профилями
 */
@Component
class ManageProfilesFetcher(
    private val callbackDataRepository: CallbackDataRepository,
    private val updatesUtil: UpdatesUtil,
    private val bot: Executor,
    private val cd2bService: Cd2bService,
    private val redisService: RedisService,
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
            update.hasMessage() && update.message.hasDocument() -> handeDocs(update, userActualizedInfo)
        }
    }

    private fun handeDocs(
        update: Update,
        userActualizedInfo: UserActualizedInfo,
    ) {
        when {
            userActualizedInfo.currentActionType == SENDING_PROPERTIES -> receiveProperties(update, userActualizedInfo)
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
            userActualizedInfo.currentActionType == CONFIRM_REMOVE_PROFILE &&
                !TextCommands.isTextCommand(text) -> removeProfile(update, userActualizedInfo, text)
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
                    buildConsole(
                        userActualizedInfo.tui,
                        update.callbackQuery.message.messageId.toString(),
                    )
                }
                startsWith("#set_port") -> {
                    resetUserData(userActualizedInfo, removeConsole = false)
                    clickSetPort(update, userActualizedInfo, callbackData)
                }
                startsWith("#upd_prop") -> {
                    resetUserData(userActualizedInfo, removeConsole = false)
                    clickUpdateProperties(update, userActualizedInfo, callbackData)
                }
                startsWith("#rm_profile") -> {
                    resetUserData(userActualizedInfo, removeConsole = false)
                    confirmRemoveProfile(update, userActualizedInfo, callbackData)
                }
                // TODO("нуждается в подтверждении???")
                // TODO: прикрутить хэши коммитов для понимания че и где
                startsWith("#rerun_upd") -> {
                    resetUserData(userActualizedInfo, removeConsole = false)
                    rerunProfile(
                        update = update,
                        userActualizedInfo = userActualizedInfo,
                        callbackData = callbackData,
                        withUpdate = true,
                    )
                }
                startsWith("#rerun_wupd") -> {
                    resetUserData(userActualizedInfo, removeConsole = false)
                    rerunProfile(
                        update = update,
                        userActualizedInfo = userActualizedInfo,
                        callbackData = callbackData,
                        withUpdate = false,
                    )
                }
                startsWith("#stop") -> {
                    resetUserData(userActualizedInfo, removeConsole = false)
                    stopProfile(update, userActualizedInfo, callbackData)
                }
            }
        }
    }

    private fun rerunProfile(
        update: Update,
        userActualizedInfo: UserActualizedInfo,
        callbackData: CallbackData,
        withUpdate: Boolean,
        logsLimit: Int = 15,
        pingInterval: Long = 2,
    ) {
        val messageId = callbackData.messageId ?: return
        val profileName = callbackData.callbackData?.split("|")?.last() ?: return
        val buildLogs = mutableListOf<String>()

        cd2bService.rerunProfile(
            profileName = profileName,
            shouldRebuild = withUpdate,
        ) { response, isClose ->
            response ?: run {
                if (!isClose) return@rerunProfile
            }

            if (response?.isNewLine == false) buildLogs.removeLast()
            response?.message?.let { buildLogs.add(it) }

            if (buildLogs.size > logsLimit) {
                buildLogs.removeFirstOrNull()
            }

            val logsMessage = buildLogs
                .joinToString { it.plus("\n") }
                .removePrefix("\n")
                .markdownFormat()

            val text = "\uD83D\uDEE0 Собираю образ контейнера для профиля `$profileName`\\, " +
                "придется подождать\\.\n" +
                "Чтобы тебе было спокойнее\\, вот логи сборки\\:\n\n" +
                "```$logsMessage".shortMessage(MAX_MSG_LENGTH - 3)
                    .plus("```")

            val dateKey = "dateKey_$profileName"
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

            val cur = LocalDateTime.now()
            val last = redisService.getSafe(dateKey)?.let {
                LocalDateTime.parse(it, formatter)
            }

            if (last == null || Duration.between(last, cur).seconds > pingInterval || isClose) {
                bot.execute(
                    EditMessageText().also {
                        it.messageId = messageId.toInt()
                        it.chatId = userActualizedInfo.tui
                        it.text = text
                        it.parseMode = ParseMode.MARKDOWNV2
                    },
                )
                redisService.setValue(
                    dateKey,
                    LocalDateTime.now().format(formatter),
                )
            }
        }

        val cancelButton = cancelButton(
            messageId,
            profileName,
            "К настройкам профиля",
        )
        val keyboard = createKeyboard(listOf(listOf(cancelButton)))

        bot.execute(
            EditMessageText().also {
                it.messageId = messageId.toInt()
                it.chatId = userActualizedInfo.tui
                it.text = "✅ Профиль успешно собран и запущен."
                it.replyMarkup = keyboard
            },
        )
    }

    private fun stopProfile(
        update: Update,
        userActualizedInfo: UserActualizedInfo,
        callbackData: CallbackData,
    ) {
        val messageId = callbackData.messageId ?: return
        val profileName = callbackData.callbackData?.split("|")?.last() ?: return

        val errorStorage = mutableListOf<Cd2bError>()
        val profileResponse = cd2bService.stopProfile(
            profileName,
            errorStorage,
        )

        val cancelButton = cancelButton(
            messageId,
            profileName,
            "К настройкам профиля",
        )
        val keyboard = createKeyboard(listOf(listOf(cancelButton)))

        if (errorStorage.any { it.statusCode != 200 } || profileResponse?.isRunning == true) {
            bot.execute(
                EditMessageText().also {
                    it.chatId = userActualizedInfo.tui
                    it.messageId = messageId.toInt()
                    it.text = "❌ Ошибка сервера"
                    it.replyMarkup = keyboard
                },
            )
        } else {
            bot.execute(
                EditMessageText().also {
                    it.chatId = userActualizedInfo.tui
                    it.messageId = messageId.toInt()
                    it.text = "⛔\uFE0F Профиль $profileName остановлен."
                    it.replyMarkup = keyboard
                },
            )
        }
    }

    private fun removeProfile(
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

        if (text != profileName) {
            bot.execute(
                EditMessageText().also {
                    it.chatId = userActualizedInfo.tui
                    it.messageId = messageId.toInt()
                    it.text = "❌ Неверное название профиля. Твое действие будет отменено."
                },
            )
            resetUserData(userActualizedInfo, removeConsole = false)
            // делаем паузу чтобы юзер увидел сообщение
            runBlocking { delay(2000) }

            showProfileInfo(
                profileName,
                messageId,
                userActualizedInfo.tui,
            )
            return
        }

        bot.execute(
            EditMessageText().also {
                it.chatId = userActualizedInfo.tui
                it.messageId = messageId.toInt()
                it.text = "Пробую удалить профиль..."
            },
        )

        val errorStorage = mutableListOf<Cd2bError>()
        cd2bService.removeProfile(
            profileName,
            errorStorage,
        )

        resetUserData(userActualizedInfo, removeConsole = false)

        if (errorStorage.any { it.statusCode != 200 }) {
            val cancelButton = cancelButton(
                messageId,
                profileName,
                "К настройкам профиля",
            )

            val keyboard = createKeyboard(listOf(listOf(cancelButton)))

            bot.execute(
                EditMessageText().also {
                    it.chatId = userActualizedInfo.tui
                    it.messageId = messageId.toInt()
                    it.text = "❌ Ошибка сервера"
                    it.replyMarkup = keyboard
                },
            )
        } else {
            val callbackBack = callbackDataRepository.save(
                CallbackData(
                    messageId = messageId,
                    callbackData = "#back_to_list",
                ),
            )

            val keyboard = createKeyboard(
                listOf(
                    listOf(
                        InlineKeyboardButton().also {
                            it.text = "Ко всем профилям"
                            it.callbackData = callbackBack.id.toString()
                        },
                    ),
                ),
            )

            bot.execute(
                EditMessageText().also {
                    it.chatId = userActualizedInfo.tui
                    it.messageId = messageId.toInt()
                    it.text = "✅\uD83D\uDDD1 _Профиль_ `$profileName` _успешно удален._"
                    it.parseMode = ParseMode.MARKDOWN
                    it.replyMarkup = keyboard
                },
            )
        }
    }
    private fun confirmRemoveProfile(
        update: Update,
        userActualizedInfo: UserActualizedInfo,
        callbackData: CallbackData,
    ) {
        val profileName = callbackData.callbackData?.split("|")?.last() ?: return
        val messageId = update.callbackQuery.message.messageId

        val cancelButton = cancelButton(
            messageId.toString(),
            profileName,
        )

        val keyboard = createKeyboard(listOf(listOf(cancelButton)))

        userActualizedInfo.currentActionType = CONFIRM_REMOVE_PROFILE
        userActualizedInfo.data = "$profileName|$messageId"

        bot.execute(
            EditMessageText().also {
                it.chatId = userActualizedInfo.tui
                it.messageId = messageId
                it.text = "‼\uFE0F_Ты собираешься_ *удалить* _профиль ${profileName}_.\n\n" +
                    "Это потенциально деструктивное действие. " +
                    "Пожалуйста, подтверди его, написав следующим сообщением название профиля."
                it.replyMarkup = keyboard
                it.parseMode = ParseMode.MARKDOWN
            },
        )
    }

    private fun clickUpdateProperties(
        update: Update,
        userActualizedInfo: UserActualizedInfo,
        callbackData: CallbackData,
    ) {
        val profileName = callbackData.callbackData?.split("|")?.last() ?: return
        val messageId = update.callbackQuery.message.messageId

        userActualizedInfo.currentActionType = SENDING_PROPERTIES
        userActualizedInfo.data = "$profileName|$messageId"

        val cancelButton = cancelButton(
            messageId.toString(),
            profileName,
        )

        val keyboard = createKeyboard(listOf(listOf(cancelButton)))

        // TODO: ссылка на текущий файл с настройками
        bot.execute(
            EditMessageText().also {
                it.chatId = userActualizedInfo.tui
                it.messageId = messageId
                it.text = "_Ты редактируешь файл_ `application.properties` _профиля_ `$profileName`.\n\n" +
                    "В следующем сообщении отправь файл с пропертями. " +
                    "Обрати внимание, что указанный там порт приложения *будет проигнорирован*."
                it.replyMarkup = keyboard
                it.parseMode = ParseMode.MARKDOWN
            },
        )
    }

    private fun receiveProperties(
        update: Update,
        userActualizedInfo: UserActualizedInfo,
    ) {
        val data = userActualizedInfo.data ?: return

        val profileName = data.split("|")[0]
        val messageId = data.split("|")[1]

        val fileUrl = updatesUtil.fileUrl(update) ?: return

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
                it.text = "Пробую обновить проперти..."
            },
        )

        val errorStorage = mutableListOf<Cd2bError>()

        val response = cd2bService.uploadProperties(
            profileName,
            fileUrl,
            errorStorage,
        )

        val cancelButton = cancelButton(
            messageId,
            profileName,
        )
        val keyboard = createKeyboard(listOf(listOf(cancelButton)))

        if (errorStorage.any { it.statusCode != 200 }) {
            bot.execute(
                EditMessageText().also {
                    it.chatId = userActualizedInfo.tui
                    it.messageId = messageId.toInt()
                    it.text = "Произошла ошибка сервера. Попробуйте загрузить файл еще раз."
                    it.replyMarkup = keyboard
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

    private fun clickSetPort(
        update: Update,
        userActualizedInfo: UserActualizedInfo,
        callbackData: CallbackData,
    ) {
        val profileName = callbackData.callbackData?.split("|")?.last() ?: return
        val messageId = update.callbackQuery.message.messageId
        val profileResponse = cd2bService.checkProfile(profileName) ?: return

        val name = profileResponse.name.markdownFormat()

        userActualizedInfo.currentActionType = TYPING_PORT
        userActualizedInfo.data = "$profileName|$messageId"

        val cancelButton = cancelButton(
            messageId.toString(),
            profileName,
        )

        val keyboard = createKeyboard(listOf(listOf(cancelButton)))

        bot.execute(
            EditMessageText().also {
                it.chatId = userActualizedInfo.tui
                it.messageId = messageId
                it.text = "_Ты редактируешь порт профиля_ `$name`.\n" +
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

        val cancelButton = cancelButton(
            messageId,
            profileName,
        )

        val keyboard = createKeyboard(listOf(listOf(cancelButton)))

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
            "\uD83D\uDCE6Имя Docker-image: $imageName\n" +
            "\uD83D\uDCC4[Логи](${cd2bService.url()}/logs/${profileResponse.imageName})"

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
        val callbackUpdProperty = callbackDataRepository.save(
            CallbackData(
                messageId = messageId,
                callbackData = "#upd_prop|$profileName",
            ),
        )
        val callbackRemove = callbackDataRepository.save(
            CallbackData(
                messageId = messageId,
                callbackData = "#rm_profile|$profileName",
            ),
        )

        // TODO: сделать рабочей кнопкой
        val propertyContentButton = if (profileResponse.hasProperties) {
            val callbackProp = callbackDataRepository.save(
                CallbackData(
                    messageId = messageId,
                    callbackData = "#show_prop|$profileName",
                ),
            )
            InlineKeyboardButton().also {
                it.text = "\uD83D\uDD16 Показать проперти"
                it.callbackData = callbackProp.id.toString()
            }
        } else { null }

        val buttons = createRunOrRerunButton(profileResponse, messageId).chunked(1).toMutableList()
        buttons.addAll(
            listOf(
                listOf(
                    InlineKeyboardButton().also {
                        it.text = "\uD83D\uDCF6 Настройка порта"
                        it.callbackData = callbackPort.id.toString()
                    },
                ),
                listOf(
                    InlineKeyboardButton().also {
                        it.text = "\uD83D\uDCDD Обновить проперти"
                        it.callbackData = callbackUpdProperty.id.toString()
                    },
                ),
                listOf(
                    InlineKeyboardButton().also {
                        it.text = "❌ Удалить профиль"
                        it.callbackData = callbackRemove.id.toString()
                    },
                ),
            ),
        )

        propertyContentButton?.let {
            buttons.add(listOf(it))
        }

        buttons.add(
            listOf(
                InlineKeyboardButton().also {
                    it.text = "◀\uFE0F Назад"
                    it.callbackData = callbackBack.id.toString()
                },
            ),
        )

        val keyboard = createKeyboard(buttons)

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

    private fun createRunOrRerunButton(
        profileResponse: ProfileResponse,
        messageId: String,
    ): List<InlineKeyboardButton> {
        val buttons = mutableListOf<InlineKeyboardButton>()

        // если пропертей нет то не рисуем кнопки для запуска
        if (!profileResponse.hasProperties) return buttons

        val callbackRerunWithUpd = callbackDataRepository.save(
            CallbackData(
                messageId = messageId,
                callbackData = "#rerun_upd|${profileResponse.name}",
            ),
        )
        val callbackRerunWithoutUpd = callbackDataRepository.save(
            CallbackData(
                messageId = messageId,
                callbackData = "#rerun_wupd|${profileResponse.name}",
            ),
        )

        if (profileResponse.isRunning) {
            val callbackStop = callbackDataRepository.save(
                CallbackData(
                    messageId = messageId,
                    callbackData = "#stop|${profileResponse.name}",
                ),
            )

            buttons.add(
                InlineKeyboardButton().also {
                    it.text = "\uD83D\uDFE2 Перезапуск без обновы"
                    it.callbackData = callbackRerunWithoutUpd.id.toString()
                },
            )
            buttons.add(
                InlineKeyboardButton().also {
                    it.text = "\uD83D\uDFE0 Перезапуск с обновой"
                    it.callbackData = callbackRerunWithUpd.id.toString()
                },
            )
            buttons.add(
                InlineKeyboardButton().also {
                    it.text = "\uD83D\uDD34 Выключить"
                    it.callbackData = callbackStop.id.toString()
                },
            )
        } else {
            buttons.add(
                InlineKeyboardButton().also {
                    it.text = "\uD83D\uDFE2 Запуск без обновы"
                    it.callbackData = callbackRerunWithoutUpd.id.toString()
                },
            )
            buttons.add(
                InlineKeyboardButton().also {
                    it.text = "\uD83D\uDFE0 Запуск с обновой"
                    it.callbackData = callbackRerunWithUpd.id.toString()
                },
            )
        }
        return buttons
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

                    val text = "❗\uFE0FОбнаружен инцидент\\. Сервис cd2b вернул код `$statusCode`\\.\n" +
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

    /**
     * Создает кнопку для возврата к консоли профиля
     */
    private fun cancelButton(
        messageId: String,
        profileName: String,
        buttonText: String = "Отмена",
    ): InlineKeyboardButton {
        val callbackBack = callbackDataRepository.save(
            CallbackData(
                messageId = messageId,
                callbackData = "#profile|$profileName",
            ),
        )

        return InlineKeyboardButton().also {
            it.text = buttonText
            it.callbackData = callbackBack.id.toString()
        }
    }
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
