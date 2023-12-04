package ru.idfedorov09.telegram.bot.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
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
        return doPost(errorStorage, "/all_profiles")
    }

    fun checkProfile(
        profileName: String,
        errorStorage: MutableList<Cd2bError> = mutableListOf(),
    ): ProfileResponse? {
        return doPost(
            errorStorage = errorStorage,
            endpoint = "/check_profile",
            params = mapOf("profile_name" to profileName),
        )
    }

    fun setPort(
        profileName: String,
        port: String,
        errorStorage: MutableList<Cd2bError> = mutableListOf(),
    ): ProfileResponse? {
        return doPost(
            errorStorage = errorStorage,
            endpoint = "/set_port",
            params = mapOf("profile_name" to profileName, "port" to port),
        )
    }

    private inline fun <reified T> doPost(
        errorStorage: MutableList<Cd2bError> = mutableListOf(),
        endpoint: String,
        params: Map<String, Any> = mapOf(),
    ): T? {
        val response: T? =
            runBlocking {
                try {
                    val response = client.post("http://127.0.0.1:8000/${endpoint.removePrefix("/")}") {
                        params.forEach {
                            parameter(it.key, it.value)
                        }
                    }

                    if (response.status.value != 200) {
                        errorStorage.addAll(catchInvalidQuery(response))
                        null
                    } else {
                        response.body<T>()
                    }
                } catch (e: Exception) {
                    errorStorage.addAll(catchException(e))
                    null
                }
            }
        return response
    }

    /**
     * Функция обрабатывающая запрос, который некорректно обработался на сервере
     */
    private suspend fun catchInvalidQuery(
        response: HttpResponse,
    ): List<Cd2bError> {
        val errorsList = mutableListOf<Cd2bError>()

        @Serializable data class ErrorDetail(val detail: String)
        val errorDetail: ErrorDetail = response.body()

        errorsList.add(
            Cd2bError(
                statusCode = response.status.value,
                statusDescription = errorDetail.detail,
            ),
        )

        return errorsList
    }
    private fun catchException(e: Exception): List<Cd2bError> {
        val errorsList = mutableListOf<Cd2bError>()

        when (e) {
            is ConnectException -> {
                Cd2bError(
                    statusCode = -1,
                    statusDescription = "Connection error",
                    stackTrace = e.stackTraceToString(),
                ).addTo(errorsList)
            }
        }

        return errorsList
    }

    private fun Cd2bError.addTo(errorStorage: MutableList<Cd2bError>) {
        errorStorage.add(this)
    }
}
