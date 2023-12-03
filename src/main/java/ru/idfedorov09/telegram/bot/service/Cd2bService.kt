package ru.idfedorov09.telegram.bot.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import ru.idfedorov09.telegram.bot.data.model.Cd2bError
import ru.idfedorov09.telegram.bot.data.model.ProfileResponse
import java.net.ConnectException

@Service
class Cd2bService {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    // TODO: настроить адрес cd2b
    fun getAllProfiles(
        errorStorage: MutableList<Cd2bError> = mutableListOf(),
    ): List<ProfileResponse>? {
        val response: List<ProfileResponse>? = runBlocking {
            try {
                client.post("http://127.0.0.1:8000/all_profiles").body()
            } catch (e: Exception) {
                val error = when (e) {
                    is ConnectException -> {
                        Cd2bError(
                            statusCode = -1,
                            statusDescription = "Connection error",
                            stackTrace = e.stackTraceToString(),
                        )
                    }
                    else -> null
                }

                error?.let { errorStorage.add(error) }
                null
            }
        }
        return response
    }
}
