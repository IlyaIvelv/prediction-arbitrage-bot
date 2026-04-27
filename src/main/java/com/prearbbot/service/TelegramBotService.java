package com.prearbbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prearbbot.config.TelegramProperties;
import com.prearbbot.core.model.ArbitrageSignal;
import com.prearbbot.core.scanner.ArbitrageEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import jakarta.annotation.PostConstruct;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBotService {

    private final TelegramProperties properties;
    private final ArbitrageEngine arbitrageEngine;
    private WebClient telegramClient;
    private ObjectMapper objectMapper = new ObjectMapper();

    private volatile boolean scanningEnabled = true;

    @PostConstruct
    private void initWebClient() {
        this.telegramClient = WebClient.builder()
                .baseUrl("https://api.telegram.org/bot" + properties.getToken())
                .build();

        log.info("✅ Telegram bot initialized with direct connection");

        // Запускаем поток для получения обновлений
        startUpdatesListener();
    }

    // Запуск слушателя команд
    private void startUpdatesListener() {
        Thread updateThread = new Thread(() -> {
            int lastUpdateId = 0;
            while (true) {
                try {
                    String url = "/getUpdates?timeout=30&offset=" + (lastUpdateId + 1);

                    String response = telegramClient.get()
                            .uri(url)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block();

                    if (response != null) {
                        JsonNode root = objectMapper.readTree(response);
                        JsonNode updates = root.get("result");

                        if (updates != null && updates.isArray()) {
                            for (JsonNode update : updates) {
                                lastUpdateId = update.get("update_id").asInt();

                                JsonNode message = update.get("message");
                                if (message != null) {
                                    String text = message.has("text") ? message.get("text").asText() : "";
                                    long chatId = message.get("chat").get("id").asLong();

                                    handleCommand(chatId, text);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Error getting updates", e);
                }
            }
        });
        updateThread.setDaemon(true);
        updateThread.start();
    }

    // Обработка команд
    private void handleCommand(long chatId, String command) {
        log.info("Received command: {} from chat: {}", command, chatId);

        switch (command.toLowerCase()) {
            case "/start":
                sendMessage(chatId, "🤖 *Бот запущен!*\n\nДоступные команды:\n/start - Запустить бота\n/stop - Остановить сканирование\n/status - Проверить статус\n/scan - Запустить сканирование вручную");
                break;

            case "/stop":
                scanningEnabled = false;
                sendMessage(chatId, "⏹️ *Сканирование остановлено*\n\nДля возобновления отправь /start");
                break;

            case "/status":
                String status = scanningEnabled ? "✅ *Работает*" : "❌ *Остановлен*";
                sendMessage(chatId, "📊 *Статус бота:*\n" + status);
                break;

            case "/scan":
                sendMessage(chatId, "🔍 *Запускаю внеочередное сканирование...*");
                arbitrageEngine.scan();
                sendMessage(chatId, "✅ *Сканирование завершено!*\nРезультаты будут в логах.");
                break;

            default:
                if (command.startsWith("/")) {
                    sendMessage(chatId, "❌ *Неизвестная команда*\n\nДоступные команды:\n/start - Запустить\n/stop - Остановить\n/status - Статус\n/scan - Сканирование");
                }
                break;
        }
    }

    // Отправка сообщения
    private void sendMessage(long chatId, String text) {
        try {
            telegramClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/sendMessage")
                            .queryParam("chat_id", chatId)
                            .queryParam("text", text)
                            .queryParam("parse_mode", "Markdown")
                            .build())
                    .retrieve()
                    .bodyToMono(Object.class)
                    .block();

            log.info("📤 Message sent to chat {}", chatId);
        } catch (Exception e) {
            log.error("❌ Failed to send message to chat {}", chatId, e);
        }
    }

    // Оригинальный метод для отправки арбитражных сигналов
    public void notifyArbitrageFound(ArbitrageSignal signal) {
        if (!scanningEnabled) {
            log.info("Scanning disabled by user, skipping notification");
            return;
        }

        String message = formatArbitrageMessage(signal);

        for (Long chatId : properties.getChatIds()) {
            sendMessage(chatId, message);
        }

        if (properties.isAutoConfirmEnabled()) {
            log.info("Auto-confirm enabled, placing bets for signal {}", signal.getId());
        }
    }

    private String formatArbitrageMessage(ArbitrageSignal signal) {
        return String.format(
                """
                🔍 *АРБИТРАЖ НАЙДЕН!* %n%n
                📊 Событие: %s%n
                💰 Профит: *%.2f%%*%n
                ✅ YES: %.4f%n
                ❌ NO: %.4f%n
                """,
                signal.getEventTitle(),
                signal.getProfitPercent(),
                signal.getPriceYes(),
                signal.getPriceNo()
        );
    }
}