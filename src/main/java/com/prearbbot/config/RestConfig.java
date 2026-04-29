package com.prearbbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestConfig {

    @Bean
    public RestClient polymarketRestClient() {
        var factory = new HttpComponentsClientHttpRequestFactory();

        // Таймауты в миллисекундах
        factory.setConnectTimeout((int) Duration.ofSeconds(30).toMillis());    // подключение
        factory.setReadTimeout((int) Duration.ofSeconds(120).toMillis());      // чтение ответа
        factory.setConnectionRequestTimeout((int) Duration.ofSeconds(30).toMillis()); // получение соединения из пула

        return RestClient.builder()
                .baseUrl("https://gamma-api.polymarket.com")
                .defaultHeader("Accept", "application/json")
                .requestFactory(factory)
                .build();
    }
}