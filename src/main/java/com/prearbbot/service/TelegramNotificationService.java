package com.prearbbot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import com.pengrad.telegrambot.model.request.ParseMode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TelegramNotificationService {

    @Value("${telegram.token}")
    private String botToken;

    private Long chatId = 241286252L;

    @Autowired
    private ArbitrageCalculatorService arbitrageService;

    private TelegramBot bot;
    private double minProfitThreshold = 0.5;

    @PostConstruct
    public void init() {
        bot = new TelegramBot(botToken);

        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {
                handleUpdate(update);
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });

        sendMessage("🤖 Арбитражный бот запущен!");
        System.out.println("🤖 Telegram бот запущен! Чат ID: " + chatId);
        System.out.println("Доступные команды: /start, /scan, /status, /proc X, /help");
    }

    private void handleUpdate(Update update) {
        if (update.message() == null) return;

        Message message = update.message();
        Long chatIdFromMsg = message.chat().id();
        String text = message.text();

        if (!this.chatId.equals(chatIdFromMsg)) {
            sendMessage(chatIdFromMsg, "⛔ У вас нет доступа к этому боту.");
            return;
        }

        if (text == null) return;

        if (text.startsWith("/start") || text.startsWith("/help")) {
            sendMessage(chatIdFromMsg, getHelpMessage());
        }
        else if (text.startsWith("/scan")) {
            sendMessage(chatIdFromMsg, "🔍 Запускаю сканирование арбитражей...");
            // Запускаем в отдельном потоке, чтобы не блокировать бота
            new Thread(() -> {
                arbitrageService.findArbitrageOpportunities(true);
            }).start();
        }
        else if (text.startsWith("/status")) {
            String status = String.format(
                    "📊 Статус бота:\n" +
                            "━━━━━━━━━━━━━━━━━━━━\n" +
                            "🤖 Бот: активен\n" +
                            "💰 Мин. порог прибыли: %.2f%%\n" +
                            "📈 Авто-уведомления: включены (при >%.2f%%)\n" +
                            "━━━━━━━━━━━━━━━━━━━━\n" +
                            "Команды: /scan - сканировать сейчас\n" +
                            "         /proc X - изменить порог\n" +
                            "         /status - текущее состояние\n" +
                            "         /help - справка",
                    minProfitThreshold, minProfitThreshold
            );
            sendMessage(chatIdFromMsg, status);
        }
        else if (text.startsWith("/proc")) {
            try {
                String[] parts = text.split(" ");
                if (parts.length >= 2) {
                    double newThreshold = Double.parseDouble(parts[1]);
                    if (newThreshold >= 0) {
                        minProfitThreshold = newThreshold;
                        sendMessage(chatIdFromMsg, String.format("✅ Минимальный порог прибыли установлен на %.2f%%", newThreshold));
                    } else {
                        sendMessage(chatIdFromMsg, "❌ Порог должен быть положительным числом");
                    }
                } else {
                    sendMessage(chatIdFromMsg, "❌ Использование: /proc 1.5 (проценты)");
                }
            } catch (NumberFormatException e) {
                sendMessage(chatIdFromMsg, "❌ Неверный формат. Используйте число, например: /proc 1.5");
            }
        }
        else {
            sendMessage(chatIdFromMsg, "❓ Неизвестная команда. Используйте /help для списка команд.");
        }
    }

    private String getHelpMessage() {
        return """
            🤖 *Арбитражный бот* 🤖
            ━━━━━━━━━━━━━━━━━━━━
            📌 *Доступные команды:*
            
            /start — показать это сообщение
            /help — помощь
            /scan — принудительное сканирование арбитражей прямо сейчас
            /status — текущие настройки
            /proc X — установить порог прибыли (X в %)
            
            ━━━━━━━━━━━━━━━━━━━━
            📊 *Примеры:*
            /proc 1.5 — уведомлять при прибыли >1.5%
            /scan — найти арбитраж прямо сейчас
            
            ━━━━━━━━━━━━━━━━━━━━
            🔔 *Авто-уведомления* приходят автоматически
            когда находится прибыль > установленного порога.
            """;
    }

    public void sendMessage(String text) {
        sendMessage(chatId, text);
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage request = new SendMessage(chatId, text);
        request.parseMode(ParseMode.HTML);
        SendResponse response = bot.execute(request);
        if (!response.isOk()) {
            System.err.println("❌ Ошибка отправки Telegram: " + response.errorCode() + " - " + response.description());
        }
    }

    public void sendArbitrageSignal(String buyExchange, String buyOutcome, double buyPrice,
                                    String sellExchange, String sellOutcome, double sellPrice,
                                    double profitPercent, String eventTitle) {
        if (profitPercent < minProfitThreshold) {
            return;
        }

        String emoji = profitPercent >= 3 ? "🟢" : (profitPercent >= 1 ? "🟡" : "🔵");
        String message = String.format("""
            %s *АРБИТРАЖ НАЙДЕН!* %s
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━
            📊 *Событие:* %s
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━
            📈 *Стратегия:*\s
               Купить %s на %s по цене %.4f
               Купить %s на %s по цене %.4f
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━
            💰 *Прибыль:* %.2f%%
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━
            """,
                emoji, emoji,
                eventTitle,
                buyOutcome, buyExchange, buyPrice,
                sellOutcome, sellExchange, sellPrice,
                profitPercent
        );

        sendMessage(message);
    }

    public double getMinProfitThreshold() {
        return minProfitThreshold;
    }
}