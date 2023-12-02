package ru.idfedorov09.telegram.bot.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import redis.clients.jedis.Jedis;
import ru.idfedorov09.telegram.bot.data.model.RedisServerData;
import ru.idfedorov09.telegram.bot.util.RedisUtil;

@Configuration
@EnableCaching
@PropertySource("classpath:${main-property:application}.properties")
public class RedisConfig extends CachingConfigurerSupport {

    @Value("${spring.redis.host}")
    private String redisHost;

    @Value("${spring.redis.port}")
    private int redisPort;

    @Value("${spring.redis.password:#{null}}")
    private String redisPassword;

    @Bean
    public RedisServerData redisServerData() {
        return new RedisServerData(
                redisPort,
                redisHost,
                redisPassword
        );
    }

    @Bean
    public Jedis jedis(RedisServerData redisServerData){
        return RedisUtil.INSTANCE.getConnection(redisServerData);
    }


}