package ru.idfedorov09.telegram.bot.util

import redis.clients.jedis.Jedis
import ru.idfedorov09.telegram.bot.data.model.RedisServerData

object RedisUtil {

    // TODO: правильно ли будет подключаться с паролем?
    fun getConnection(redisServerData: RedisServerData) = redisServerData.run {
        /*
        val config = RedisStandaloneConfiguration(host, port)
        password?.let { config.setPassword(it) }
        JedisConnectionFactory(config).connection.nativeConnection as Jedis
         */
        Jedis(host, port).also { jedis ->
            password?.let { jedis.auth(it) }
        }
    }
}
