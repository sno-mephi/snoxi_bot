package ru.idfedorov09.telegram.bot.fetchers.bot

import io.ktor.websocket.*
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
import ru.idfedorov09.telegram.bot.data.enums.UserActionType
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
import java.util.regex.Pattern

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
        val text = runCatching { update.message.text }.getOrNull()

        when {
            update.hasCallbackQuery() -> handleButtons(update, userActualizedInfo)
            update.hasMessage() && update.message.hasText() -> handleText(update, userActualizedInfo, text!!)
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
        val someAction = { actionType: UserActionType ->
            userActualizedInfo.currentActionType == actionType && !TextCommands.isTextCommand(text)
        }
        when {
            someAction(TYPING_PORT) -> setPort(update, userActualizedInfo, text)
            someAction(CONFIRM_REMOVE_PROFILE) -> removeProfile(update, userActualizedInfo, text)
            someAction(TYPING_PROPERTY_KEY_VALUE) -> updatePropertyField(update, userActualizedInfo, text)
            someAction(TYPING_PROFILE_NAME) -> validateNewProfileNameAndNext(update, userActualizedInfo, text)
            someAction(TYPING_PROFILE_REPO_URL) -> validateNewProfileRepoLink(update, userActualizedInfo, text)
            someAction(TYPING_NEW_PROFILE_PORT) -> createNewProfileWithPortValidation(update, userActualizedInfo, text)
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
                    clickNewProfile(update, userActualizedInfo, callbackData)
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
                startsWith("#show_prop") -> {
                    resetUserData(userActualizedInfo, removeConsole = false)
                    showPropertyMenu(update, userActualizedInfo, callbackData)
                }
                startsWith("#in_prop_upd_field") -> {
                    resetUserData(userActualizedInfo, removeConsole = false)
                    clickUpdatePropertyField(update, userActualizedInfo, callbackData)
                }
            }
        }
    }

    private fun createNewProfileWithPortValidation(
        update: Update,
        userActualizedInfo: UserActualizedInfo,
        text: String,
    ) {
        val consoleMessageId = userActualizedInfo.data!!.split("|")[0]
        val profileName = userActualizedInfo.data!!.split("|")[1]
        val githubLink = userActualizedInfo.data!!.split("|")[2]
        val port = text.trim()

        bot.execute(
            DeleteMessage().also {
                it.chatId = userActualizedInfo.tui
                it.messageId = update.message.messageId
            },
        )

        val keyboard = { msg: String ->
            val backButton = toProfilesListButton(
                messageId = consoleMessageId,
                buttonText = msg,
            )
            createKeyboard(listOf(listOf(backButton)))
        }

        if (!isValidPort(port)) {
            bot.execute(
                EditMessageText().also {
                    it.chatId = userActualizedInfo.tui
                    it.messageId = consoleMessageId.toInt()
                    it.text = "❌ Неправильный формат порта. Попробуй еще раз.\n\n" +
                        "\uD83D\uDC40 *Обрати внимание*, что порт является целым числом из отрезка `[1; 65535]`"
                    it.parseMode = ParseMode.MARKDOWN
                    it.replyMarkup = keyboard("Отмена")
                },
            )
            return
        }

        bot.execute(
            EditMessageText().also {
                it.chatId = userActualizedInfo.tui
                it.messageId = consoleMessageId.toInt()
                it.text = "Пробую создать профиль..."
            },
        )

        val errorStorage = mutableListOf<Cd2bError>()
        cd2bService.createProfile(profileName, githubLink, port, errorStorage)

        if (errorStorage.any { it.statusCode != 200 }) {
            bot.execute(
                EditMessageText().also {
                    it.chatId = userActualizedInfo.tui
                    it.messageId = consoleMessageId.toInt()
                    it.text = "❌ Ошибка сервера."
                    it.replyMarkup = keyboard("Ко всем профилям")
                },
            )
            resetUserData(userActualizedInfo, removeConsole = false)
        } else {
            val callbackBack = callbackDataRepository.save(
                CallbackData(
                    messageId = consoleMessageId,
                    callbackData = "#back_to_list",
                ),
            )
            val callbackToProfile = callbackDataRepository.save(
                CallbackData(
                    messageId = consoleMessageId,
                    callbackData = "#profile|$profileName",
                ),
            )

            bot.execute(
                EditMessageText().also {
                    it.chatId = userActualizedInfo.tui
                    it.messageId = consoleMessageId.toInt()
                    it.text = "✅ Профиль `$profileName` успешно создан."
                    it.parseMode = ParseMode.MARKDOWN
                    it.replyMarkup = createKeyboard(
                        listOf(
                            listOf(
                                InlineKeyboardButton().also {
                                    it.text = "К настройкам профиля"
                                    it.callbackData = callbackToProfile.id.toString()
                                },
                            ),
                            listOf(
                                InlineKeyboardButton().also {
                                    it.text = "Ко всем профилям"
                                    it.callbackData = callbackBack.id.toString()
                                },
                            ),
                        ),
                    )
                },
            )
            resetUserData(userActualizedInfo, removeConsole = false)
        }
    }
    private fun validateNewProfileRepoLink(
        update: Update,
        userActualizedInfo: UserActualizedInfo,
        text: String,
    ) {
        val consoleMessageId = userActualizedInfo.data!!.split("|")[0]
        val profileName = userActualizedInfo.data!!.split("|")[1]
        val githubLink = text.trim()

        val backButton = toProfilesListButton(
            messageId = consoleMessageId,
            buttonText = "Отмена",
        )
        val keyboard = createKeyboard(listOf(listOf(backButton)))

        bot.execute(
            DeleteMessage().also {
                it.chatId = userActualizedInfo.tui
                it.messageId = update.message.messageId
            },
        )

        if (!isGitHubRepository(githubLink)) {
            val githubHttpsDescLink = "https://docs.github.com/en/get-started/" +
                "getting-started-with-git/about-remote-repositories"

            bot.execute(
                EditMessageText().also {
                    it.chatId = userActualizedInfo.tui
                    it.messageId = consoleMessageId.toInt()
                    it.text = "❌ Некорректный формат имени. Попробуй еще раз.\n\n" +
                        "\uD83D\uDC40 *Обрати внимание*, что на текущий момент из VCS поддерживается " +
                        "только [GitHub]($githubHttpsDescLink). Приватные репозитории " +
                        "пока не поддерживаются."
                    it.parseMode = ParseMode.MARKDOWN
                    it.replyMarkup = keyboard
                },
            )
            return
        }

        bot.execute(
            EditMessageText().also {
                it.chatId = userActualizedInfo.tui
                it.messageId = consoleMessageId.toInt()
                it.text = "\uD83E\uDDBE _Ты создаешь новый профиль_ *$profileName*. " +
                    "В следующем сообщении укажи порт, на котором будет работать приложение.\n\n" +
                    "\uD83D\uDC40 *Обрати внимание*, что порт является целым числом из отрезка `[1; 65535]`"
                it.parseMode = ParseMode.MARKDOWN
                it.replyMarkup = keyboard
            },
        )

        userActualizedInfo.currentActionType = TYPING_NEW_PROFILE_PORT
        userActualizedInfo.data = "$consoleMessageId|$profileName|$githubLink"
    }

    private fun validateNewProfileNameAndNext(
        update: Update,
        userActualizedInfo: UserActualizedInfo,
        text: String,
    ) {
        val profileName = text.trim()
        val messageId = userActualizedInfo.data ?: return
        val backButton = toProfilesListButton(
            messageId = messageId,
            buttonText = "Отмена",
        )

        bot.execute(
            DeleteMessage().also {
                it.chatId = userActualizedInfo.tui
                it.messageId = update.message.messageId
            },
        )

        val keyboard = createKeyboard(listOf(listOf(backButton)))

        val regex = Regex("^[a-zA-Z_][a-zA-Z0-9_]{0,19}$")

        if (!regex.matches(profileName)) {
            bot.execute(
                EditMessageText().also {
                    it.chatId = userActualizedInfo.tui
                    it.messageId = messageId.toInt()
                    it.text = "❌ Неправильный формат имени. Попробуй еще раз.\n\n" +
                        "\uD83D\uDC40 *Обрати внимание*, что имя должно содержать только латинские буквы, " +
                        "цифры или символы подчеркивания, а также не должно начинаться с цифр. " +
                        "Максимальная длина имени - 20 символов."
                    it.parseMode = ParseMode.MARKDOWN
                    it.replyMarkup = keyboard
                },
            )
            return
        }

        val githubHttpsDescLink = "https://docs.github.com/en/get-started/" +
            "getting-started-with-git/about-remote-repositories"

        bot.execute(
            EditMessageText().also {
                it.chatId = userActualizedInfo.tui
                it.messageId = messageId.toInt()
                it.text = "\uD83E\uDDBE _Ты создаешь новый профиль_ *$profileName*. " +
                    "В следующем сообщении укажи https-ссылку на репозиторий.\n\n" +
                    "\uD83D\uDC40 *Обрати внимание*, что на текущий момент из VCS поддерживается " +
                    "только [GitHub]($githubHttpsDescLink). Приватные репозитории пока не поддерживаются."
                it.parseMode = ParseMode.MARKDOWN
                it.replyMarkup = keyboard
            },
        )

        userActualizedInfo.currentActionType = TYPING_PROFILE_REPO_URL
        userActualizedInfo.data = "$messageId|$profileName"
    }
    private fun clickNewProfile(
        update: Update,
        userActualizedInfo: UserActualizedInfo,
        callbackData: CallbackData,
    ) {
        val backButton = toProfilesListButton(
            messageId = update.callbackQuery.message.messageId.toString(),
            buttonText = "Отмена",
        )
        val keyboard = createKeyboard(listOf(listOf(backButton)))

        bot.execute(
            EditMessageText().also {
                it.chatId = userActualizedInfo.tui
                it.messageId = update.callbackQuery.message.messageId
                it.text = "\uD83E\uDDBE _Ты создаешь новый профиль_. " +
                    "В следующем сообщении укажи имя профиля.\n\n" +
                    "\uD83D\uDC40 *Обрати внимание*, что имя должно содержать только латинские буквы, " +
                    "цифры или символы подчеркивания, а также не должно начинаться с цифр. " +
                    "Максимальная длина имени - 20 символов."
                it.parseMode = ParseMode.MARKDOWN
                it.replyMarkup = keyboard
            },
        )
        userActualizedInfo.currentActionType = TYPING_PROFILE_NAME
        userActualizedInfo.data = callbackData.messageId
    }

    private fun updatePropertyField(
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

        if (text.trim().split("=").size != 2) {
            val cancelButton = cancelButton(
                messageId,
                profileName,
            )
            val keyboard = createKeyboard(listOf(listOf(cancelButton)))

            bot.execute(
                EditMessageText().also {
                    it.chatId = userActualizedInfo.tui
                    it.messageId = messageId.toInt()
                    it.text = "❌ Неверный формат, попробуй еще раз. \n" +
                        "Отправь мне настройку вида: ```\nsome.key=some_value\n```\n"
                    it.parseMode = ParseMode.MARKDOWN
                    it.replyMarkup = keyboard
                },
            )
            return
        }

        val cancelButton = cancelButton(
            messageId,
            profileName,
            "К настройкам профиля",
        )

        val keyboard = createKeyboard(listOf(listOf(cancelButton)))

        val key = text.trim().split("=")[0]
        val value = text.trim().split("=")[1]

        val errorStorage = mutableListOf<Cd2bError>()
        cd2bService.changePropertiesField(profileName, key, value, errorStorage)
        if (errorStorage.any { it.statusCode != 200 }) {
            bot.execute(
                EditMessageText().also {
                    it.chatId = userActualizedInfo.tui
                    it.messageId = messageId.toInt()
                    it.text = "❌ Ошибка сервера или неверный формат. Попробуй еще раз. \n" +
                        "Отправь мне настройку вида: ```\nsome.key=some_value\n```"
                    it.parseMode = ParseMode.MARKDOWN
                    it.replyMarkup = keyboard
                },
            )
        } else {
            bot.execute(
                EditMessageText().also {
                    it.chatId = userActualizedInfo.tui
                    it.messageId = messageId.toInt()
                    it.text = "✅ Успешно обновлено / добавлено следующее свойство:```\n" +
                        "$key=$value\n```"
                    it.parseMode = ParseMode.MARKDOWN
                    it.replyMarkup = keyboard
                },
            )
            resetUserData(userActualizedInfo, removeConsole = false)
        }
    }
    private fun clickUpdatePropertyField(
        update: Update,
        userActualizedInfo: UserActualizedInfo,
        callbackData: CallbackData,
    ) {
        val messageId = callbackData.messageId ?: return
        val profileName = callbackData.callbackData?.split("|")?.last() ?: return

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
                it.text = "_Ты собираешься добавить/изменить проперти профиля_ `$profileName`.\n\n" +
                    "Следующим сообщением отправь мне настройку вида: ```\nsome.key=some_value\n```\n" +
                    "Если соответствующий ключ уже есть, он будет обновлен, если нет - свойство будет создано."
                it.parseMode = ParseMode.MARKDOWN
                it.replyMarkup = keyboard
            },
        )

        userActualizedInfo.currentActionType = TYPING_PROPERTY_KEY_VALUE
        userActualizedInfo.data = "$profileName|$messageId"
    }

    private fun showPropertyMenu(
        update: Update,
        userActualizedInfo: UserActualizedInfo,
        callbackData: CallbackData,
    ) {
        val messageId = callbackData.messageId ?: return
        val profileName = callbackData.callbackData?.split("|")?.last() ?: return
        val profileResponse = cd2bService.checkProfile(profileName) ?: return

        val cancelButton = cancelButton(
            messageId,
            profileName,
            "К настройкам профиля",
        )

        val callbackUpdPropertyField = callbackDataRepository.save(
            CallbackData(
                messageId = messageId,
                callbackData = "#in_prop_upd_field|$profileName",
            ),
        )

        val keyboard = createKeyboard(
            listOf(
                listOf(
                    InlineKeyboardButton().also {
                        it.text = "Добавить/изменить свойство"
                        it.callbackData = callbackUpdPropertyField.id.toString()
                    },
                ),
                listOf(cancelButton),
            ),
        )

        val properties = profileResponse.propertyContent
            ?.split("\n")
            ?.filter { !(it.trim().startsWith("#") || it.trim() == "") }
            ?.joinToString(separator = "\n") { it.trim() }
            ?.removeSuffix("\n")

        bot.execute(
            EditMessageText().also {
                it.chatId = userActualizedInfo.tui
                it.messageId = messageId.toInt()
                it.text = "✏\uFE0F Проперти профиля `$profileName`:```application.properties\n" +
                    "$properties\n```"
                it.parseMode = ParseMode.MARKDOWN
                it.replyMarkup = keyboard
            },
        )
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
        lateinit var closeStatus: CloseReason

        cd2bService.rerunProfile(
            profileName = profileName,
            shouldRebuild = withUpdate,
        ) { response, isClose, closeReason ->
            closeReason?.let { closeStatus = closeReason }
            response ?: run {
                if (!isClose) return@rerunProfile
            }

            if (response?.isNewLine == false) buildLogs.removeLast()
            response?.message?.let { buildLogs.add(it) }

            if (buildLogs.size > logsLimit) {
                buildLogs.removeFirstOrNull()
            }

            val logsMessage = buildLogs
                .joinToString(separator = "\n") { it }
                .removePrefix("\n")
                .markdownFormat()

            val text = "\uD83D\uDEE0 Собираю образ контейнера для профиля `$profileName`\\, " +
                "придется подождать\\.\n" +
                "\uD83D\uDE0C Чтобы тебе было спокойнее\\, вот логи сборки\\:\n\n" +
                "```Docker\n$logsMessage".shortMessage(MAX_MSG_LENGTH - 3)
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

        runBlocking { delay(2000) }

        val textMessage = if (closeStatus.code.toInt() == 1000) {
            "✅ Профиль успешно собран и запущен."
        } else {
            "❌ Ошибка запуска или сборки.\nУбедись, что ранее ты запускал профиль со сборкой. " +
                "Если запускал - попробуй запустить локально, так станет понятнее, в чем проблема ;)"
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
                it.text = textMessage
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
            val backButton = toProfilesListButton(messageId)
            val keyboard = createKeyboard(listOf(listOf(backButton)))

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

        if (errorStorage.any { it.statusCode != 200 }) {
            val cancelButton = cancelButton(
                messageId,
                profileName,
            )
            val keyboard = createKeyboard(listOf(listOf(cancelButton)))

            bot.execute(
                EditMessageText().also {
                    it.chatId = userActualizedInfo.tui
                    it.messageId = messageId.toInt()
                    it.text = "Не могу обновить проперти. Возможно, формат твоего файла неверный"
                    it.replyMarkup = keyboard
                },
            )
        } else {
            userActualizedInfo.currentActionType = null
            userActualizedInfo.data = null

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
                    it.text = "✅ Проперти успешно обновлены. Не забудь перезагрузить профиль \uD83D\uDE09"
                    it.replyMarkup = keyboard
                },
            )
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
            ),
        )

        propertyContentButton?.let {
            buttons.add(listOf(it))
        }
        buttons.add(
            listOf(
                InlineKeyboardButton().also {
                    it.text = "❌ Удалить профиль"
                    it.callbackData = callbackRemove.id.toString()
                },
            ),
        )
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

        val callback = callbackDataRepository.save(
            CallbackData(
                messageId = messageId,
                callbackData = "new profile",
            ),
        )

        chunkedProfiles.add(
            mutableListOf(
                InlineKeyboardButton().also {
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

    private fun toProfilesListButton(
        messageId: String,
        buttonText: String = "Ко всем профилям",
    ): InlineKeyboardButton {
        val callbackBack = callbackDataRepository.save(
            CallbackData(
                messageId = messageId,
                callbackData = "#back_to_list",
            ),
        )

        return InlineKeyboardButton().also {
            it.text = buttonText
            it.callbackData = callbackBack.id.toString()
        }
    }

    // TODO: не нравится мне что cd2b и бот для валидации могут использовать разные условия,
    // могут появиться расхождения. TODO("добавить в cd2b нужные для валидации ручки")
    private fun isGitHubRepository(link: String): Boolean {
        val githubRepoPattern = Pattern.compile("^https?://github\\.com/[\\w\\-]+/[\\w\\-]+\\.git$")
        val matcher = githubRepoPattern.matcher(link)
        return matcher.matches()
    }

    fun isValidPort(port: String): Boolean {
        return try {
            val number = port.toInt()
            number in 1..65535
        } catch (e: NumberFormatException) {
            false
        }
    }

    data class CallbackKeyboardStorage(
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
