package com.prearbbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling    // Включаем @Scheduled для сканера
@EnableAsync         // Включаем асинхронность для Telegram/ставок
public class PrearbBotApplication {
    public static void main(String[] args) {
        SpringApplication.run(PrearbBotApplication.class, args);
    }
}