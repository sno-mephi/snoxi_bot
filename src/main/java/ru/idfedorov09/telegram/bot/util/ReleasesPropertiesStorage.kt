package ru.idfedorov09.telegram.bot.util

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ru.idfedorov09.telegram.bot.data.model.ProfilePropertiesBase

@Component
class ReleasesPropertiesStorage(

    @Value("\${router.port.first}")
    private val firstPort: Int,

    @Value("\${router.port.second}")
    private val secondPort: Int,

    @Value("\${router.port.testing}")
    private val testPort: Int,

    @Value("\${test.router.telegram.bot.token}")
    private val testBotToken: String,

    @Value("\${test.router.telegram.bot.name}")
    private val testBotName: String,

    @Value("\${test.router.postgres.url}")
    private val testBotPgUrl: String,

    @Value("\${test.router.postgres.username}")
    private val testBotPgUsername: String,

    @Value("\${test.router.postgres.password}")
    private val testBotPgPassword: String,

    @Value("\${test.redis.host}")
    private val testRedisHost: String,

    @Value("\${test.redis.port}")
    private val testRedisPort: Int,

    @Value("\${test.redis.password}")
    private val testRedisPassword: String,

    @Value("\${production.router.telegram.bot.token}")
    private val prodBotToken: String,

    @Value("\${production.router.telegram.bot.name}")
    private val prodBotName: String,

    @Value("\${production.router.postgres.url}")
    private val prodBotPgUrl: String,

    @Value("\${production.router.postgres.username}")
    private val prodBotPgUsername: String,

    @Value("\${production.router.postgres.password}")
    private val prodBotPgPassword: String,

    @Value("\${production.redis.host}")
    private val prodRedisHost: String,

    @Value("\${production.redis.port}")
    private val prodRedisPort: Int,

    @Value("\${production.redis.password}")
    private val prodRedisPassword: String,
) {
    lateinit var test: ProfilePropertiesBase
    lateinit var prodGeneral: ProfilePropertiesBase
    lateinit var prod1: ProfilePropertiesBase
    lateinit var prod2: ProfilePropertiesBase

    @PostConstruct
    fun postConstruct() {
        test = ProfilePropertiesBase(
            token = testBotToken,
            name = testBotName,
            port = testPort,
            postgresUrl = testBotPgUrl,
            postgresUsername = testBotPgUsername,
            postgresPassword = testBotPgPassword,
            redisHost = testRedisHost,
            redisPort = testRedisPort,
            redisPassword = testRedisPassword,
        )

        prodGeneral = ProfilePropertiesBase(
            token = prodBotToken,
            name = prodBotName,
            port = 0,
            postgresUrl = prodBotPgUrl,
            postgresUsername = prodBotPgUsername,
            postgresPassword = prodBotPgPassword,
            redisHost = prodRedisHost,
            redisPort = prodRedisPort,
            redisPassword = prodRedisPassword,
        )

        prod1 = prodGeneral.copy(port = firstPort)
        prod2 = prodGeneral.copy(port = secondPort)
    }
}
