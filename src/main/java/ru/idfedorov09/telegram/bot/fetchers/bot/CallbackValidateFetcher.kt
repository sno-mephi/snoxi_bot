package ru.idfedorov09.telegram.bot.fetchers.bot

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Update
import ru.idfedorov09.telegram.bot.data.model.UserActualizedInfo
import ru.idfedorov09.telegram.bot.executor.Executor
import ru.idfedorov09.telegram.bot.flow.ExpContainer
import ru.idfedorov09.telegram.bot.repo.CallbackDataRepository
import ru.idfedorov09.telegram.bot.util.UpdatesUtil
import ru.mephi.sno.libs.flow.belly.InjectData
import ru.mephi.sno.libs.flow.fetcher.GeneralFetcher
import kotlin.jvm.optionals.getOrNull

/**
 * Фетчер, валидирующий нажатия на кнопки
 * если в бдшке не нашелся коллбэк - показать это
 */
@Component
class CallbackValidateFetcher(
    private val bot: Executor,
    private val callbackDataRepository: CallbackDataRepository,
    private val updatesUtil: UpdatesUtil,
) : GeneralFetcher() {

    @InjectData
    fun doFetch(
        update: Update,
        exp: ExpContainer,
    ) {
        if (!update.hasCallbackQuery()) return
        val chatId = updatesUtil.getChatId(update)
        val id = update.callbackQuery.id.toLongOrNull()
        val callbackInfo = id?.let { callbackDataRepository.findById(id).getOrNull() }
        callbackInfo ?: run {
            bot.execute(
                EditMessageText().also {
                    it.chatId = chatId
                    it.messageId = update.callbackQuery.message.messageId
                    it.text = "_Это сообщение устарело_"
                    it.parseMode = ParseMode.MARKDOWN
                },
            )
            exp.isValid = false
        }
    }
}
