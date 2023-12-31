package ru.idfedorov09.telegram.bot.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.idfedorov09.telegram.bot.data.model.Cd2bError
import ru.idfedorov09.telegram.bot.data.model.ProfileBuildMessageResponse
import ru.idfedorov09.telegram.bot.data.model.ProfileCreateResponse
import ru.idfedorov09.telegram.bot.data.model.ProfileResponse
import java.net.ConnectException
import java.net.URL

@Service
class Cd2bService {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
        install(WebSockets)
        install(HttpTimeout) {
            requestTimeoutMillis = Long.MAX_VALUE
        }
    }

    @Value("\${cd2b.host:127.0.0.1}")
    private lateinit var cd2bHost: String

    @Value("\${cd2b.port:8000}")
    private var cd2bPort: Int = 8000

    @Value("\${cd2b.login:ROOT}")
    private lateinit var login: String

    @Value("\${cd2b.password:8000}")
    private lateinit var password: String

    private lateinit var userRequest: UserRequest

    fun getAllProfiles(
        errorStorage: MutableList<Cd2bError> = mutableListOf(),
    ): List<ProfileResponse>? {
        return doPost(errorStorage, "/all_profiles", body = userRequest)
    }

    fun url() = URL("http", cd2bHost, cd2bPort, "").toString()

    @PostConstruct
    private fun setCredentials() {
        userRequest = UserRequest(login = login, password = password)
    }

    fun setCredentials(login: String, password: String) {
        userRequest = UserRequest(login = login, password = password)
    }

    fun checkProfile(
        profileName: String,
        errorStorage: MutableList<Cd2bError> = mutableListOf(),
    ): ProfileResponse? {
        return doPost(
            errorStorage = errorStorage,
            endpoint = "/check_profile",
            params = mapOf("profile_name" to profileName),
            body = userRequest,
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
            body = userRequest,
        )
    }

    fun uploadProperties(
        profileName: String,
        fileUrl: String,
        errorStorage: MutableList<Cd2bError> = mutableListOf(),
    ): ProfileResponse? {
        return doPost(
            errorStorage = errorStorage,
            endpoint = "/upload_prop",
            params = mapOf("profile_name" to profileName, "file_url" to fileUrl),
            body = userRequest,
        )
    }

    fun removeProfile(
        profileName: String,
        errorStorage: MutableList<Cd2bError> = mutableListOf(),
    ): ProfileResponse? {
        return doPost(
            errorStorage = errorStorage,
            endpoint = "/remove",
            params = mapOf("profile_name" to profileName),
            body = userRequest,
        )
    }

    fun stopProfile(
        profileName: String,
        errorStorage: MutableList<Cd2bError> = mutableListOf(),
    ): ProfileResponse? {
        return doPost(
            errorStorage = errorStorage,
            endpoint = "/stop",
            params = mapOf("profile_name" to profileName),
            body = userRequest,
        )
    }

    fun changePropertiesField(
        profileName: String,
        propertyKey: String,
        propertyValue: String,
        errorStorage: MutableList<Cd2bError> = mutableListOf(),
    ): ProfileResponse? {
        return doPost(
            errorStorage = errorStorage,
            endpoint = "/change_properties_field",
            params = mapOf(
                "profile_name" to profileName,
                "key" to propertyKey,
                "value" to propertyValue,
            ),
            body = userRequest,
        )
    }

    fun createProfile(
        profileName: String,
        repoLink: String,
        port: String,
        errorStorage: MutableList<Cd2bError> = mutableListOf(),
    ): ProfileCreateResponse? {
        @Serializable
        data class ProfileRequest(
            val name: String,
            val github: String,
            val port: Int = 5613,
            @SerialName("post_proc")
            val postProc: Boolean? = null,
        )

        @Serializable
        data class ProfileCreateRequestBody(
            @SerialName("profile_request")
            val profileRequest: ProfileRequest,
            @SerialName("user_request")
            val userRequest: UserRequest,
        )

        val profileRequest = ProfileRequest(
            name = profileName,
            github = repoLink,
            port = port.toInt(),
        )

        return doPost(
            errorStorage = errorStorage,
            endpoint = "/create_profile",
            body = ProfileCreateRequestBody(
                profileRequest,
                userRequest,
            ),
        )
    }

    /**
     * По вебсокету перезапускает профиль с указанными настройками.
     * При получении обновления от сервера выполняет метод receiveTextAction
     */
    fun rerunProfile(
        profileName: String,
        externalPort: Int = -1,
        shouldRebuild: Boolean = true,
        receiveTextAction: (ProfileBuildMessageResponse?, Boolean, CloseReason?) -> Unit = { _, _, _ -> },
    ) {
        runBlocking {
            client.webSocket(
                method = HttpMethod.Get,
                host = cd2bHost,
                port = cd2bPort,
                path = "/rerun?" +
                    "profile_name=$profileName" +
                    "&external_port=$externalPort" +
                    "&rebuild=$shouldRebuild" +
                    "&login=${userRequest.login}" +
                    "&password=${userRequest.password}",
            ) {
                while (true) {
                    val receive = incoming.receiveCatching()
                    if (receive.isClosed || receive.isFailure) {
                        val closeReason = this.closeReason.await()
                        receiveTextAction(null, true, closeReason)
                        break
                    }
                    val receivedText = (receive.getOrNull() as? Frame.Text)?.readText() ?: continue

                    val response: ProfileBuildMessageResponse = Json.decodeFromString(receivedText)
                    receiveTextAction(response, false, null)
                }
            }
        }
    }

    private inline fun <reified T> doPost(
        errorStorage: MutableList<Cd2bError> = mutableListOf(),
        endpoint: String,
        params: Map<String, Any> = mapOf(),
        body: Any? = null,
        timeout: Long? = 20000,
    ): T? {
        val response: T? =
            runBlocking {
                try {
                    val response = client.post("${url()}/${endpoint.removePrefix("/")}") {
                        params.forEach {
                            parameter(it.key, it.value)
                        }
                        body?.let {
                            contentType(ContentType.Application.Json)
                            setBody(body)
                        }
                        timeout {
                            requestTimeoutMillis = timeout
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
            else -> {
                Cd2bError(
                    statusCode = -666,
                    statusDescription = "Unknown error",
                    stackTrace = e.stackTraceToString(),
                ).addTo(errorsList)
            }
        }

        return errorsList
    }

    private fun Cd2bError.addTo(errorStorage: MutableList<Cd2bError>) {
        errorStorage.add(this)
    }

    @Serializable
    private data class UserRequest(
        val login: String,
        val password: String,
    )
}
