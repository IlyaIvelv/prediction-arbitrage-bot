package com.prearbbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prearbbot.config.TelegramProperties;
import com.prearbbot.core.model.ArbitrageSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import jakarta.annotation.PostConstruct;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBotService {

    private final TelegramProperties properties;
    private final ScanningStatusService statusService;
    private WebClient telegramClient;
    private ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    private void initWebClient() {
        this.telegramClient = WebClient.builder()
                .baseUrl("https://api.telegram.org/bot" + properties.getToken())
                .build();

        log.info("✅ Telegram bot initialized");
        startUpdatesListener();
    }

    private void startUpdatesListener() {
        Thread updateThread = new Thread(() -> {
            int lastUpdateId = 0;
            while (true) {
                try {
                    String response = telegramClient.get()
                            .uri("/getUpdates?timeout=30&offset=" + (lastUpdateId + 1))
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

    private void handleCommand(long chatId, String command) {
        log.info("Command: {} from {}", command, chatId);

        switch (command.toLowerCase()) {
            case "/start":
                statusService.start();
                sendMessage(chatId, "✅ *Бот запущен!*\n\nСканирование активно.\n\nДоступные команды:\n/stop - Остановить\n/status - Статус\n/scan - Ручное сканирование");
                break;

            case "/stop":
                statusService.stop();
                sendMessage(chatId, "⏹️ *Бот остановлен*\n\nДля запуска отправь /start");
                break;

            case "/status":
                sendMessage(chatId, "📊 *Статус:* " + statusService.getStatus());
                break;

            case "/scan":
                if (statusService.isEnabled()) {
                    sendMessage(chatId, "🔍 *Запускаю ручное сканирование...*");
                    statusService.triggerManualScan();
                    sendMessage(chatId, "✅ *Сканирование запущено!*");
                } else {
                    sendMessage(chatId, "⏸️ *Бот остановлен*\nСначала запусти /start");
                }
                break;

            default:
                if (command.startsWith("/")) {
                    sendMessage(chatId, "❌ *Команды:*\n/start - Запустить\n/stop - Остановить\n/status - Статус\n/scan - Ручное сканирование");
                }
                break;
        }
    }

    private void sendMessage(long chatId, String text) {
        try {
            telegramClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/sendMessage")
                            .queryParam("chat_id", chatId)
                            .queryParam("text", text)
                            .queryParam("parse_mode", "Markdown")
                            .queryParam("disable_web_page_preview", false)
                            .build())
                    .retrieve()
                    .bodyToMono(Object.class)
                    .block();
            log.info("✅ Message sent to chat {}", chatId);
        } catch (Exception e) {
            log.error("❌ Failed to send message to chat {}", chatId, e);
        }
    }

    public void notifyArbitrageFound(ArbitrageSignal signal) {
        if (!statusService.isEnabled()) {
            log.info("Scanning disabled, skipping notification");
            return;
        }

        log.info("📢 Sending arbitrage notification: {} ({}% profit)", signal.getEventTitle(), signal.getProfitPercent());

        String message = formatArbitrageMessage(signal);

        for (Long chatId : properties.getChatIds()) {
            sendMessage(chatId, message);
        }
    }

    private String formatArbitrageMessage(ArbitrageSignal signal) {
        return String.format(
                """
                🔍 *АРБИТРАЖ НАЙДЕН!*
                
                📊 *Событие:* %s
                💰 *Профит:* %.2f%%
                
                ───────────────────
                📈 *Купить YES (ДА)*
                💵 Цена: *%.4f*
                🔗 [Открыть сделку YES](%s)
                
                📉 *Купить NO (НЕТ)*
                💵 Цена: *%.4f*
                🔗 [Открыть сделку NO](%s)
                ───────────────────
                
                ⚡️ *Действуйте быстро!*
                Сигнал действителен 5 минут
                """,
                signal.getEventTitle(),
                signal.getProfitPercent(),
                signal.getPriceYes(),
                signal.getUrlYes(),
                signal.getPriceNo(),
                signal.getUrlNo()
        );
    }
}