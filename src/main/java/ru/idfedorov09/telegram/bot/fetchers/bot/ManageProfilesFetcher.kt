package ru.idfedorov09.telegram.bot.fetchers.bot

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import ru.idfedorov09.telegram.bot.data.enums.TextCommands
import ru.idfedorov09.telegram.bot.data.model.UserActualizedInfo
import ru.idfedorov09.telegram.bot.executor.Executor
import ru.idfedorov09.telegram.bot.service.Cd2bService
import ru.idfedorov09.telegram.bot.util.UpdatesUtil
import ru.mephi.sno.libs.flow.belly.InjectData
import ru.mephi.sno.libs.flow.fetcher.GeneralFetcher

/**
 * Фетчер для управления профилями
 */
@Component
class ManageProfilesFetcher(
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
            text == null && update.hasCallbackQuery() -> handleButtons(update, userActualizedInfo)
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
        bot.execute(
            SendMessage().also {
                it.chatId = userActualizedInfo.tui
                it.text = "Собираю информацию о профилях..."
            },
        )
        buildConsole()
    }

    private fun buildConsole() {
        val profiles = cd2bService.getAllProfiles()
        TODO()
    }

    private fun createKeyboard(keyboard: List<List<InlineKeyboardButton>>) =
        InlineKeyboardMarkup().also { it.keyboard = keyboard }
}
