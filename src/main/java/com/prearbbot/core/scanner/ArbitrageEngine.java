package com.prearbbot.core.scanner;

import com.prearbbot.config.ArbitrageProperties;
import com.prearbbot.core.model.ArbitrageSignal;
import com.prearbbot.core.repository.ArbitrageSignalRepository;
import com.prearbbot.service.TelegramBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArbitrageEngine {


    private final TelegramBotService telegramBotService;


    private final ArbitrageSignalRepository repository;
    private final ArbitrageProperties properties;

    /**
     * Сканер арбитражей по таймеру
     */
    @Scheduled(fixedDelayString = "${arbitrage.scan-interval}")
    public void scan() {
        log.info("🔄 Starting arbitrage scan cycle...");

        try {
            // 1. Получаем цены с площадок (заглушка — потом реализуем клиенты)
            var polyMarkets = fetchPolymarketPrices();
            var kalshiMarkets = fetchKalshiPrices();

            // 2. Ищем арбитражи
            int found = findAndSaveArbitrages(polyMarkets, kalshiMarkets);
            log.info("✅ Scan completed. Found {} arbitrage opportunities", found);

        } catch (Exception e) {
            log.error("💥 Scan cycle failed", e);
        }
    }

    private List<MarketPrice> fetchPolymarketPrices() {
        log.debug("🔍 Fetching Polymarket prices (stub)");
        return List.of(
                new MarketPrice("Will Trump win 2024?", BigDecimal.valueOf(0.22), BigDecimal.valueOf(0.51), "https://polymarket.com/trump-2024"),
                new MarketPrice("Will Biden drop out?", BigDecimal.valueOf(0.35), BigDecimal.valueOf(0.68), "https://polymarket.com/biden-out")
        );
    }

//    public void scan() {
//        findAndSaveArbitrages();
//    }

    private List<MarketPrice> fetchKalshiPrices() {
        log.debug("🔍 Fetching Kalshi prices (stub)");
        return List.of(
                new MarketPrice("Will Trump win 2024?", BigDecimal.valueOf(0.53), BigDecimal.valueOf(0.50), "https://kalshi.com/markets/TRUMP-2024"),
                new MarketPrice("Will Biden drop out?", BigDecimal.valueOf(0.37), BigDecimal.valueOf(0.65), "https://kalshi.com/markets/BIDEN-OUT")
        );
    }

    private int findAndSaveArbitrages(List<MarketPrice> poly, List<MarketPrice> kalshi) {
        int count = 0;
        for (var p : poly) {
            for (var k : kalshi) {
                if (isSameEvent(p.title(), k.title())) {
                    var arb = calculateArbitrage(p, k);
                    if (arb != null && isProfitable(arb)) {
                        saveArbitrage(arb);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private boolean isSameEvent(String a, String b) {
        // Упрощённый матчинг (в будущем — NLP / AI)
        return a.toLowerCase().contains(b.toLowerCase()) ||
                b.toLowerCase().contains(a.toLowerCase());
    }

    private ArbitrageSignal calculateArbitrage(MarketPrice poly, MarketPrice kalshi) {
        // Формула: YES + NO < 1.0 → арбитраж
        BigDecimal sum = poly.yesPrice().add(kalshi.noPrice());
        if (sum.compareTo(BigDecimal.ONE) >= 0) return null;

        BigDecimal profit = BigDecimal.ONE.divide(sum, 4, BigDecimal.ROUND_HALF_UP)
                .subtract(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(100)); // в %

        return ArbitrageSignal.builder()
                .eventTitle(poly.title())
                .platformYes("polymarket")
                .platformNo("kalshi")
                .priceYes(poly.yesPrice())
                .priceNo(kalshi.noPrice())
                .profitPercent(profit)
                .urlYes(poly.url())
                .urlNo(kalshi.url())
                .spreadPercent(sum.subtract(BigDecimal.ONE).abs())
                .expectedProfit(profit)
                .status("NEW")
                .createdAt(OffsetDateTime.now())
                .build();
    }

    private boolean isProfitable(ArbitrageSignal arb) {
        return arb.getProfitPercent().compareTo(BigDecimal.valueOf(properties.getTrading().getMinProfitPercent())) >= 0;
    }

    private void saveArbitrage(ArbitrageSignal arb) {
        log.info("🎯 Arbitrage found: {} | Profit: {}% | YES: {} | NO: {}",
                arb.getEventTitle(), arb.getProfitPercent(), arb.getPriceYes(), arb.getPriceNo());

        // Сохраняем в БД
        repository.save(arb);

        // отправить в Telegram
        log.info("🎯 Arbitrage found: {} | Profit: {}% | YES: {} | NO: {}",
                arb.getEventTitle(), arb.getProfitPercent(), arb.getPriceYes(), arb.getPriceNo());

        // Сохраняем в БД
        repository.save(arb);

        // Отправляем в Telegram
        telegramBotService.notifyArbitrageFound(arb);
    }

    // Вспомогательная запись
    public record MarketPrice(String title, BigDecimal yesPrice, BigDecimal noPrice, String url) {}
}