package ru.idfedorov09.telegram.bot.service

import com.google.gson.Gson
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import java.net.URL

@Component
class RouterService(
    private val gson: Gson,
) : TelegramLongPollingBot() {
    @Value("\${router.telegram.bot.token}")
    private lateinit var token: String

    @Value("\${router.telegram.bot.name}")
    private lateinit var name: String

    @Value("\${router.telegram.bot.reconnect-pause:1000}")
    private var reconnectPause: Long = 1000

    @Value("\${router.port.first:9441}")
    private var firstPort: Int = 9441

    @Value("\${router.port.second:9442}")
    private var secondPort: Int = 9442

    /** если true, то запросы кидаются на порт 1, если false то на порт 2**/
    private val isFirstActive: Boolean = true

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    @Value("\${router.main.bot.host:127.0.0.1}")
    private lateinit var botHost: String

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 20000
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RouterService::class.java)
    }

    override fun onUpdateReceived(update: Update) {
        coroutineScope.launch(Dispatchers.Default) {
            sendUpdate(gson.toJson(update), url())
        }
    }

    @OptIn(InternalAPI::class)
    private suspend fun sendUpdate(updateJson: String, url: String, timeout: Long = 20000) {
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            body = updateJson
            timeout {
                requestTimeoutMillis = timeout
            }
        }
        if (response.status.value != 200) {
            // TODO: обработать случай неудачной обработки сервером (ботом) обновления
        }
    }

    @PostConstruct
    fun botConnect() {
        lateinit var telegramBotsApi: TelegramBotsApi

        try {
            telegramBotsApi = TelegramBotsApi(DefaultBotSession::class.java)
        } catch (e: TelegramApiException) {
            log.error("Can't create API: $e. Trying to reconnect..")
            botConnect()
        }

        try {
            telegramBotsApi.registerBot(this)
            log.info("TelegramAPI started. Look for messages")
        } catch (e: TelegramApiException) {
            log.error("Can't Connect. Pause " + reconnectPause / 1000 + "sec and try again. Error: " + e.message)
            try {
                Thread.sleep(reconnectPause)
            } catch (threadError: InterruptedException) {
                log.error(threadError.message)
                return
            }
            botConnect()
        }
    }

    override fun getBotUsername() = name
    override fun getBotToken() = token
    private fun portResolve() = if (isFirstActive) firstPort else secondPort
    private fun url() = URL("http", botHost, portResolve(), "").toString()
}
