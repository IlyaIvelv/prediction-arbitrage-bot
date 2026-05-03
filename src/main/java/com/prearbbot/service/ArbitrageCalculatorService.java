package com.prearbbot.service;

import com.prearbbot.core.model.RawEvent;
import com.prearbbot.core.repository.RawEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ArbitrageCalculatorService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RawEventRepository rawEventRepository;

    public void findArbitrageOpportunities() {
        System.out.println("\n💰 ПОИСК АРБИТРАЖНЫХ ВОЗМОЖНОСТЕЙ...");

        // Получаем все найденные связки из event_matches
        String sql = "SELECT id, event_id_1, event_id_2 FROM event_matches";

        List<Map<String, Object>> matches = jdbcTemplate.queryForList(sql);
        System.out.println("📊 Проверяю " + matches.size() + " связок на арбитраж...");

        int opportunitiesFound = 0;

        for (Map<String, Object> match : matches) {
            Long matchId = ((Number) match.get("id")).longValue();
            Long eventId1 = ((Number) match.get("event_id_1")).longValue();
            Long eventId2 = ((Number) match.get("event_id_2")).longValue();

            Optional<RawEvent> geminiOpt = rawEventRepository.findById(eventId1);
            Optional<RawEvent> polyOpt = rawEventRepository.findById(eventId2);

            if (!geminiOpt.isPresent() || !polyOpt.isPresent()) continue;

            RawEvent geminiEvent = geminiOpt.get();
            RawEvent polyEvent = polyOpt.get();

            // Проверяем 4 комбинации:
            // 1. Купить YES на Gemini, купить NO на Polymarket
            checkCombination(matchId, "gemini", "YES", geminiEvent.getYesPrice(),
                    "polymarket", "NO", polyEvent.getNoPrice());

            // 2. Купить NO на Gemini, купить YES на Polymarket
            checkCombination(matchId, "gemini", "NO", geminiEvent.getNoPrice(),
                    "polymarket", "YES", polyEvent.getYesPrice());

            // 3. Купить YES на Polymarket, купить NO на Gemini
            checkCombination(matchId, "polymarket", "YES", polyEvent.getYesPrice(),
                    "gemini", "NO", geminiEvent.getNoPrice());

            // 4. Купить NO на Polymarket, купить YES на Gemini
            checkCombination(matchId, "polymarket", "NO", polyEvent.getNoPrice(),
                    "gemini", "YES", geminiEvent.getYesPrice());
        }

        System.out.println("🎯 Найдено арбитражных возможностей: " + opportunitiesFound);
    }

    private void checkCombination(Long matchId, String buyExchange, String buyOutcome, BigDecimal buyPrice,
                                  String sellExchange, String sellOutcome, BigDecimal sellPrice) {
        if (buyPrice == null || sellPrice == null) return;

        BigDecimal total = buyPrice.add(sellPrice);
        BigDecimal target = BigDecimal.ONE;

        if (total.compareTo(target) < 0) {
            BigDecimal profit = target.subtract(total);
            BigDecimal profitPercent = profit.multiply(new BigDecimal("100"));

            // Сохраняем возможность
            String sql = """
                INSERT INTO arbitrage_opportunities 
                (event_match_id, buy_exchange, buy_outcome, buy_price, sell_exchange, sell_outcome, sell_price, profit_percent)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

            try {
                jdbcTemplate.update(sql, matchId, buyExchange, buyOutcome, buyPrice,
                        sellExchange, sellOutcome, sellPrice, profitPercent);
                opportunitiesFound++;
                System.out.printf("   💰 АРБИТРАЖ! Прибыль: %.2f%%\n", profitPercent);
                System.out.printf("      Купить %s на %s по цене %.4f\n", buyOutcome, buyExchange, buyPrice);
                System.out.printf("      Купить %s на %s по цене %.4f\n", sellOutcome, sellExchange, sellPrice);
                System.out.printf("      Сумма: %.4f + %.4f = %.4f (меньше 1.0)\n", buyPrice, sellPrice, total);
            } catch (Exception e) {
                // Возможно, уже есть такой арбитраж
            }
        }
    }

    // Добавим счетчик найденных возможностей
    private int opportunitiesFound = 0;
}