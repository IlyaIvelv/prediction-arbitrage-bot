package com.prearbbot.core.scanner;

import com.prearbbot.config.ArbitrageProperties;
import com.prearbbot.core.model.ArbitrageSignal;
import com.prearbbot.core.model.MarketPrice;
import com.prearbbot.core.parser.KalshiParser;
import com.prearbbot.core.parser.PolymarketParser;
import com.prearbbot.core.repository.ArbitrageSignalRepository;
import com.prearbbot.service.ScanningStatusService;
import com.prearbbot.service.TelegramBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArbitrageEngine {

    private final TelegramBotService telegramBotService;
    private final ScanningStatusService statusService;
    private final ArbitrageSignalRepository repository;
    private final ArbitrageProperties properties;
    private final PolymarketParser polymarketParser;
    private final KalshiParser kalshiParser;

    @Scheduled(fixedDelayString = "${arbitrage.scan-interval:30000}")
    public void scheduledScan() {
        if (!statusService.isEnabled()) {
            log.debug("Сканирование остановлено пользователем");
            return;
        }
        performScan();
    }

    @EventListener
    public void onManualScan(ScanningStatusService.ManualScanEvent event) {
        log.info("🖐️ Ручное сканирование по команде /scan");
        performScan();
    }

    private void performScan() {
        log.info("🔄 Запуск сканирования арбитражей...");

        try {
            // Реальные данные с парсеров
            List<MarketPrice> polymarketMarkets = polymarketParser.fetchActiveMarkets();
            List<MarketPrice> kalshiMarkets = kalshiParser.fetchActiveEvents();

            log.info("📊 Polymarket: {} рынков, Kalshi: {} рынков", polymarketMarkets.size(), kalshiMarkets.size());

            // Ищем арбитражи
            int found = findAndSaveArbitrages(polymarketMarkets, kalshiMarkets);
            log.info("✅ Сканирование завершено. Найдено {} арбитражных возможностей", found);

        } catch (Exception e) {
            log.error("❌ Ошибка при сканировании", e);
        }
    }

    private int findAndSaveArbitrages(List<MarketPrice> poly, List<MarketPrice> kalshi) {
        int count = 0;

        for (var polyMarket : poly) {
            for (var kalshiMarket : kalshi) {
                if (isSameEvent(polyMarket.getTitle(), kalshiMarket.getTitle())) {

                    // Арбитраж: YES Polymarket + NO Kalshi
                    var arb1 = calculateArbitrage(polyMarket, kalshiMarket);
                    if (arb1 != null && isProfitable(arb1)) {
                        saveArbitrage(arb1);
                        count++;
                    }

                    // Арбитраж: NO Polymarket + YES Kalshi
                    var arb2 = calculateArbitrage(kalshiMarket, polyMarket);
                    if (arb2 != null && isProfitable(arb2)) {
                        saveArbitrage(arb2);
                        count++;
                    }
                }
            }
        }

        return count;
    }

    private boolean isSameEvent(String title1, String title2) {
        String t1 = title1.toLowerCase();
        String t2 = title2.toLowerCase();

        // Упрощенное сравнение по ключевым словам
        String[] keywords = {"trump", "biden", "fed", "rate", "election", "president"};

        for (String keyword : keywords) {
            if (t1.contains(keyword) && t2.contains(keyword)) {
                return true;
            }
        }

        return t1.contains(t2) || t2.contains(t1);
    }

    private ArbitrageSignal calculateArbitrage(MarketPrice platformYes, MarketPrice platformNo) {
        BigDecimal sum = platformYes.getYesPrice().add(platformNo.getNoPrice());

        if (sum.compareTo(BigDecimal.ONE) >= 0) {
            return null;
        }

        BigDecimal profit = BigDecimal.ONE.divide(sum, 4, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(100));

        if (profit.compareTo(BigDecimal.valueOf(0.5)) < 0) {
            return null; // Слишком маленькая прибыль
        }

        return ArbitrageSignal.builder()
                .eventTitle(platformYes.getTitle())
                .platformYes(platformYes.getPlatform())
                .platformNo(platformNo.getPlatform())
                .priceYes(platformYes.getYesPrice())
                .priceNo(platformNo.getNoPrice())
                .profitPercent(profit)
                .urlYes(platformYes.getUrl())
                .urlNo(platformNo.getUrl())
                .spreadPercent(BigDecimal.ONE.subtract(sum).abs().multiply(BigDecimal.valueOf(100)))
                .expectedProfit(profit)
                .status("NEW")
                .createdAt(OffsetDateTime.now())
                .build();
    }

    private boolean isProfitable(ArbitrageSignal arb) {
        double minProfit = properties.getTrading().getMinProfitPercent();
        return arb.getProfitPercent().doubleValue() >= minProfit;
    }

    private void saveArbitrage(ArbitrageSignal arb) {
        log.info("🎯 АРБИТРАЖ: {} | Прибыль: {}%",
                arb.getEventTitle(),
                arb.getProfitPercent().setScale(2, RoundingMode.HALF_UP)
        );

        try {
            repository.save(arb);
            telegramBotService.notifyArbitrageFound(arb);
        } catch (Exception e) {
            log.error("❌ Ошибка при сохранении", e);
        }
    }
}