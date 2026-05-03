package com.prearbbot.service;

import com.prearbbot.core.model.RawEvent;
import com.prearbbot.core.repository.RawEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ArbitrageCalculatorService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RawEventRepository rawEventRepository;

    @Autowired
    private TelegramNotificationService telegramService;

    private int opportunitiesFound = 0;

    // Класс для хранения арбитражной возможности
    private static class ArbitrageOpportunity {
        String buyExchange;
        String buyOutcome;
        double buyPrice;
        String sellExchange;
        String sellOutcome;
        double sellPrice;
        double profitPercent;
        String eventTitle;

        // Для уникальности (чтобы не дублировать одинаковые пары)
        String getUniqueKey() {
            return buyExchange + buyOutcome + sellExchange + sellOutcome + eventTitle;
        }
    }

    public void findArbitrageOpportunities() {
        findArbitrageOpportunities(false);
    }

    public void findArbitrageOpportunities(boolean sendToTelegram) {
        System.out.println("\n💰 ПОИСК АРБИТРАЖНЫХ ВОЗМОЖНОСТЕЙ...");
        opportunitiesFound = 0;

        // Собираем все возможности в список
        List<ArbitrageOpportunity> allOpportunities = new ArrayList<>();

        String sql = "SELECT id, event_id_1, event_id_2 FROM event_matches";

        List<Map<String, Object>> matches = jdbcTemplate.queryForList(sql);
        System.out.println("📊 Проверяю " + matches.size() + " связок на арбитраж...");

        for (Map<String, Object> match : matches) {
            Long matchId = ((Number) match.get("id")).longValue();
            Long eventId1 = ((Number) match.get("event_id_1")).longValue();
            Long eventId2 = ((Number) match.get("event_id_2")).longValue();

            Optional<RawEvent> geminiOpt = rawEventRepository.findById(eventId1);
            Optional<RawEvent> polyOpt = rawEventRepository.findById(eventId2);

            if (!geminiOpt.isPresent() || !polyOpt.isPresent()) continue;

            RawEvent geminiEvent = geminiOpt.get();
            RawEvent polyEvent = polyOpt.get();

            String eventTitle = geminiEvent.getTitle();
            if (eventTitle.length() > 50) {
                eventTitle = eventTitle.substring(0, 47) + "...";
            }

            // Проверяем 4 комбинации и добавляем в список
            addOpportunity(allOpportunities, matchId, "gemini", "YES", geminiEvent.getYesPrice(),
                    "polymarket", "NO", polyEvent.getNoPrice(), eventTitle);

            addOpportunity(allOpportunities, matchId, "gemini", "NO", geminiEvent.getNoPrice(),
                    "polymarket", "YES", polyEvent.getYesPrice(), eventTitle);

            addOpportunity(allOpportunities, matchId, "polymarket", "YES", polyEvent.getYesPrice(),
                    "gemini", "NO", geminiEvent.getNoPrice(), eventTitle);

            addOpportunity(allOpportunities, matchId, "polymarket", "NO", polyEvent.getNoPrice(),
                    "gemini", "YES", geminiEvent.getYesPrice(), eventTitle);
        }

        // Удаляем дубликаты по уникальному ключу
        Map<String, ArbitrageOpportunity> uniqueOpportunities = new LinkedHashMap<>();
        for (ArbitrageOpportunity opp : allOpportunities) {
            String key = opp.getUniqueKey();
            if (!uniqueOpportunities.containsKey(key) ||
                    uniqueOpportunities.get(key).profitPercent < opp.profitPercent) {
                uniqueOpportunities.put(key, opp);
            }
        }

        // Сортируем по убыванию прибыли
        List<ArbitrageOpportunity> sortedOpportunities = new ArrayList<>(uniqueOpportunities.values());
        sortedOpportunities.sort((a, b) -> Double.compare(b.profitPercent, a.profitPercent));

        opportunitiesFound = sortedOpportunities.size();

        System.out.println("🎯 Найдено уникальных арбитражных возможностей: " + opportunitiesFound);

        // Выводим в консоль в отсортированном порядке
        if (!sortedOpportunities.isEmpty()) {
            System.out.println("\n📊 ОТСОРТИРОВАННЫЕ ПО ПРИБЫЛИ АРБИТРАЖИ:");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            for (int i = 0; i < Math.min(20, sortedOpportunities.size()); i++) {
                ArbitrageOpportunity opp = sortedOpportunities.get(i);
                System.out.printf("%d. 💰 Прибыль: %.2f%%%n", i + 1, opp.profitPercent);
                System.out.printf("   Купить %s на %s по цене %.4f%n", opp.buyOutcome, opp.buyExchange, opp.buyPrice);
                System.out.printf("   Купить %s на %s по цене %.4f%n", opp.sellOutcome, opp.sellExchange, opp.sellPrice);
                System.out.printf("   Событие: %s%n", opp.eventTitle);
                System.out.println("   ─────────────────────────────────────────────");
            }
        }

        // Отправляем в Telegram, если нужно
        if (sendToTelegram && telegramService != null && !sortedOpportunities.isEmpty()) {
            sendCombinedTelegramMessage(sortedOpportunities);
        }
    }

    private void addOpportunity(List<ArbitrageOpportunity> list, Long matchId,
                                String buyExchange, String buyOutcome, BigDecimal buyPrice,
                                String sellExchange, String sellOutcome, BigDecimal sellPrice,
                                String eventTitle) {
        if (buyPrice == null || sellPrice == null) return;

        BigDecimal total = buyPrice.add(sellPrice);
        BigDecimal target = BigDecimal.ONE;

        if (total.compareTo(target) < 0) {
            BigDecimal profit = target.subtract(total);
            BigDecimal profitPercent = profit.multiply(new BigDecimal("100"));

            String sql = """
                INSERT INTO arbitrage_opportunities 
                (event_match_id, buy_exchange, buy_outcome, buy_price, sell_exchange, sell_outcome, sell_price, profit_percent)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

            try {
                jdbcTemplate.update(sql, matchId, buyExchange, buyOutcome, buyPrice,
                        sellExchange, sellOutcome, sellPrice, profitPercent);

                ArbitrageOpportunity opp = new ArbitrageOpportunity();
                opp.buyExchange = buyExchange;
                opp.buyOutcome = buyOutcome;
                opp.buyPrice = buyPrice.doubleValue();
                opp.sellExchange = sellExchange;
                opp.sellOutcome = sellOutcome;
                opp.sellPrice = sellPrice.doubleValue();
                opp.profitPercent = profitPercent.doubleValue();
                opp.eventTitle = eventTitle;
                list.add(opp);

            } catch (Exception e) {
                // Возможно, уже есть такой арбитраж
            }
        }
    }

    private void sendCombinedTelegramMessage(List<ArbitrageOpportunity> opportunities) {
        if (opportunities.isEmpty()) return;

        StringBuilder message = new StringBuilder();
        message.append("📊 *АРБИТРАЖНЫЕ ВОЗМОЖНОСТИ* 📊\n");
        message.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        message.append("Отсортировано по убыванию прибыли\n");
        message.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        int count = 0;
        for (ArbitrageOpportunity opp : opportunities) {
            if (count >= 15) {
                message.append("\n⋯ и ещё ").append(opportunities.size() - 15).append(" возможностей ⋯\n");
                break;
            }
            count++;

            String emoji = opp.profitPercent >= 3 ? "🟢" : (opp.profitPercent >= 1 ? "🟡" : "🔵");
            message.append(String.format("%s *%.2f%%* %s\n", emoji, opp.profitPercent, emoji));
            message.append("└ Купить `").append(opp.buyOutcome).append("` на *").append(opp.buyExchange).append("* по `").append(String.format("%.4f", opp.buyPrice)).append("`\n");
            message.append("└ Купить `").append(opp.sellOutcome).append("` на *").append(opp.sellExchange).append("* по `").append(String.format("%.4f", opp.sellPrice)).append("`\n");
            message.append("└ *Событие:* ").append(opp.eventTitle).append("\n\n");
        }

        message.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        message.append("🔔 *Авто-сканирование каждые 30 сек*\n");
        message.append("⚡ Для изменения порога: /proc X");

        telegramService.sendMessage(message.toString());
    }
}