package ru.idfedorov09.telegram.bot.service

import com.google.gson.Gson
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import ru.idfedorov09.telegram.bot.util.CoroutineManager
import ru.idfedorov09.telegram.bot.util.ReleasesPropertiesStorage
import java.net.URL

@Component
class RouterService(
    private val gson: Gson,
    private val coroutineManager: CoroutineManager,
    private val propertiesStorage: ReleasesPropertiesStorage,
) : TelegramLongPollingBot() {

    /** если true, то запросы кидаются на порт 1, если false то на порт 2**/
    var isFirstActive: Boolean = true

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
        coroutineManager.doAsync {
            sendUpdate(gson.toJson(update), url())
        }
    }

    private suspend fun sendUpdate(updateJson: String, url: String, timeout: Long = 20000) {
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(updateJson)
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
        val reconnectPause: Long = 1000

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

    override fun getBotUsername() = propertiesStorage.prodGeneral.name
    override fun getBotToken() = propertiesStorage.prodGeneral.token
    private fun portResolve() = if (isFirstActive) propertiesStorage.prod1.port else propertiesStorage.prod2.port
    private fun url() = URL("http", botHost, portResolve(), "").toString()
}
