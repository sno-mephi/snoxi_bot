package ru.idfedorov09.telegram.bot.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import redis.clients.jedis.Jedis
import java.time.format.DateTimeFormatter

/**
 * Сервис для работы с редис.
 * Все методы помечаем @Synchronized для избежания рейс кондишнов!
 */
@Service
class RedisService(
    private var jedis: Jedis,
) {

    companion object {
        private val log = LoggerFactory.getLogger(RedisService::class.java)
        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    @Synchronized
    fun getSafe(key: String): String? {
        return jedis.get(key)
    }

    @Synchronized
    fun getValue(key: String?): String? {
        return jedis[key]
    }

    @Synchronized
    fun setValue(key: String, value: String?) {
        value ?: run {
            jedis.del(key)
            return
        }
        jedis[key] = value
    }

    @Synchronized
    fun lpop(key: String): String? {
        return jedis.lpop(key)
    }

    @Synchronized
    fun rpush(key: String, value: String) {
        jedis.rpush(key, value)
    }

    @Synchronized
    fun keys(key: String): Set<String> {
        return jedis.keys(key)
    }

    @Synchronized
    fun del(key: String) {
        jedis.del(key)
    }

    @Synchronized
    fun del(keys: Set<String>) {
        jedis.del(*keys.toTypedArray<String>())
    }
}
