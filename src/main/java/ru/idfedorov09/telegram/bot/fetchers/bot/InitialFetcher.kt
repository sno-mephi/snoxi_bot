
package ru.idfedorov09.telegram.bot.fetchers.bot

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import ru.idfedorov09.telegram.bot.data.GlobalConstants.BASE_ADMIN_ID
import ru.idfedorov09.telegram.bot.data.enums.TextCommands
import ru.idfedorov09.telegram.bot.data.model.SnoxiUser
import ru.idfedorov09.telegram.bot.data.model.UserActualizedInfo
import ru.idfedorov09.telegram.bot.executor.Executor
import ru.idfedorov09.telegram.bot.flow.ExpContainer
import ru.idfedorov09.telegram.bot.repo.SnoxiUserRepository
import ru.idfedorov09.telegram.bot.repo.UserRepository
import ru.idfedorov09.telegram.bot.util.UpdatesUtil
import ru.mephi.sno.libs.flow.belly.InjectData
import ru.mephi.sno.libs.flow.fetcher.GeneralFetcher

/**
 * Фетчер, собирающий userActualizedInfo и обрабатывающий команду /start
 */
@Component
class InitialFetcher(
    private val userRepository: UserRepository,
    private val snoxiUserRepository: SnoxiUserRepository,
    private val updatesUtil: UpdatesUtil,
    private val bot: Executor,
) : GeneralFetcher() {
    companion object {
        private val log = LoggerFactory.getLogger(InitialFetcher::class.java)
    }

    @InjectData
    fun doFetch(
        update: Update,
        exp: ExpContainer,
    ): UserActualizedInfo? {
        val chatId = updatesUtil.getChatId(update) ?: return null
        val tui = updatesUtil.getUserId(update) ?: return null
        val text = updatesUtil.getText(update) ?: ""

        val user = userRepository.findByTui(tui) ?: run {
            bot.execute(
                SendMessage(
                    tui,
                    "\uD83E\uDD14 Я тебя не знаю. Для подробностей напиши в @snomephi_bot.",
                ),
            )
            return null
        }

        // TODO: потом пригодится
        val snoxiUser = snoxiUserRepository.findByTui(tui)
            ?: SnoxiUser(tui = tui)

        val userActualizedInfo = UserActualizedInfo(
            snoxiId = snoxiUser.id,
            tui = tui,
            lastTgNick = user.lastTgNick,
            fullName = user.fullName,
            studyGroup = user.studyGroup,
            roles = user.roles,
        )

        if (chatId == tui) {
            exp.byUser = true
        } else {
            exp.byChat = true
        }

        val isAdmin = (tui == BASE_ADMIN_ID) || "ROOT" in user.roles

        if (!isAdmin) {
            bot.execute(
                SendMessage(
                    tui,
                    "\uD83D\uDE05 Кажется, ты не входишь в состав стаффа Команды Цифровизации. " +
                        "Если думаешь, что это ошибка, создай обращение в @snomephi_bot.",
                ),
            )
            return null
        }

        // Если пользователь впервые пишет боту после получения доступа, отправляем это
        if (snoxiUser.id == null) {
            val mainKeyboard = ReplyKeyboardMarkup().also {
                it.keyboard = listOf(
                    KeyboardRow().also { it.add(TextCommands.MANAGE_PROFILES.commandText) },
                    KeyboardRow().also { it.add(TextCommands.FEATURE_REALISE.commandText) },
                    KeyboardRow().also { it.add(TextCommands.HELP.commandText) },
                )
                it.oneTimeKeyboard = true
                it.resizeKeyboard = true
            }

            bot.execute(
                SendMessage().also {
                    it.text = "Привет! Я — Снокси, внутренний бот СНО \uD83D\uDE03. \n" +
                        "Но это ты уже знаешь, верно? Что же, тогда приступим к работе!"
                    it.chatId = tui
                    it.replyMarkup = mainKeyboard
                },
            )
        }

        exp.isValid = true
        return userActualizedInfo
    }
}
