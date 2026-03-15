package com.gabon.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 **/

@Configuration
public class RedissonConfiguration {


    private static final Logger log = LoggerFactory.getLogger(RedissonConfiguration.class);
    @Value("${spring.data.redis.host}")
    private String redisHost;


    @Value("${spring.data.redis.port}")
    private String redisPort;


    @Value("${spring.data.redis.password}")
    private String redisPwd;


    /**
     * 配置分布式锁的redisson
     *
     * @return
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        log.info("redis://" + redisHost + ":" + redisPort + "password:" + redisPwd);
        //单机方式
        config.useSingleServer().setPassword(redisPwd).setAddress("redis://" + redisHost + ":" + redisPort);

        //集群
        //config.useClusterServers().addNodeAddress("redis://192.31.21.1:6379","redis://192.31.21.2:6379")

        RedissonClient redissonClient = Redisson.create(config);
        return redissonClient;
    }

    /**
     * 集群模式
     * 备注：可以用"rediss://"来启用SSL连接
     */
    /*@Bean
    public RedissonClient redissonClusterClient() {
        Config config = new Config();
        config.useClusterServers().setScanInterval(2000) // 集群状态扫描间隔时间，单位是毫秒
              .addNodeAddress("redis://127.0.0.1:7000")
              .addNodeAddress("redis://127.0.0.1:7002");
        RedissonClient redisson = Redisson.create(config);
        return redisson;
    }*/


}
