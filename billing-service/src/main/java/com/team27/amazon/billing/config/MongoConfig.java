package com.team27.amazon.billing.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class MongoConfig {

    @Value("${spring.data.mongodb.host:amazon-mongo}")
    private String host;

    @Value("${spring.data.mongodb.port:27017}")
    private int port;

    @Value("${spring.data.mongodb.database:amazonmongo}")
    private String database;

    @Value("${spring.data.mongodb.username:root}")
    private String username;

    @Value("${spring.data.mongodb.password:rootpass}")
    private String password;

    @Value("${spring.data.mongodb.authentication-database:admin}")
    private String authDatabase;

    @Bean
    public MongoClient mongoClient() {
        String uri = String.format(
            "mongodb://%s:%s@%s:%d/%s?authSource=%s",
            username, password, host, port, database, authDatabase
        );
        return MongoClients.create(uri);
    }

    @Bean
    public MongoTemplate mongoTemplate() {
        return new MongoTemplate(mongoClient(), database);
    }
}