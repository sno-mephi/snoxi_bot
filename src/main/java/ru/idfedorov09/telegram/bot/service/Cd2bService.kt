package ru.idfedorov09.telegram.bot.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import ru.idfedorov09.telegram.bot.data.model.ProfileResponse

@Service
class Cd2bService {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    // TODO: настроить адрес cd2b
    fun getAllProfiles(): List<ProfileResponse> {
        val response: List<ProfileResponse> = runBlocking {
            client.post("http://127.0.0.1:8000/all_profiles").body()
        }
        return response
    }
}
