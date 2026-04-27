package com.prearbbot.core.scanner;

import com.prearbbot.config.ArbitrageProperties;
import com.prearbbot.core.model.ArbitrageSignal;
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
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArbitrageEngine {

    private final TelegramBotService telegramBotService;
    private final ScanningStatusService statusService;
    private final ArbitrageSignalRepository repository;
    private final ArbitrageProperties properties;

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
        if (!statusService.isEnabled()) {
            log.warn("Бот остановлен, но ручное сканирование выполнено");
        }
        performScan();
    }

    private void performScan() {
        log.info("🔄 Запуск сканирования арбитражей...");

        try {
            // Получаем цены с Polymarket
            var polyMarkets = fetchPolymarketPrices();
            log.debug("Получено {} событий с Polymarket", polyMarkets.size());

            // Получаем цены с Kalshi
            var kalshiMarkets = fetchKalshiPrices();
            log.debug("Получено {} событий с Kalshi", kalshiMarkets.size());

            // Ищем арбитражи
            int found = findAndSaveArbitrages(polyMarkets, kalshiMarkets);
            log.info("✅ Сканирование завершено. Найдено {} арбитражных возможностей", found);

        } catch (Exception e) {
            log.error("❌ Ошибка при сканировании", e);
        }
    }

    /**
     * Заглушка для получения цен с Polymarket
     * В реальности здесь будет API запрос к Polymarket
     */
    private List<MarketPrice> fetchPolymarketPrices() {
        log.debug("📡 Запрос цен с Polymarket (stub)");

        // TODO: Заменить на реальный API запрос
        return List.of(
                new MarketPrice(
                        "Will Trump win 2024?",
                        BigDecimal.valueOf(0.45),  // цена YES
                        BigDecimal.valueOf(0.55),  // цена NO
                        "https://polymarket.com/market/trump-2024"
                ),
                new MarketPrice(
                        "Will Biden drop out?",
                        BigDecimal.valueOf(0.30),
                        BigDecimal.valueOf(0.70),
                        "https://polymarket.com/market/biden-drop-out"
                ),
                new MarketPrice(
                        "Will Fed cut rates in June?",
                        BigDecimal.valueOf(0.65),
                        BigDecimal.valueOf(0.35),
                        "https://polymarket.com/market/fed-rates"
                )
        );
    }

    /**
     * Заглушка для получения цен с Kalshi
     * В реальности здесь будет API запрос к Kalshi
     */
    private List<MarketPrice> fetchKalshiPrices() {
        log.debug("📡 Запрос цен с Kalshi (stub)");

        // TODO: Заменить на реальный API запрос
        return List.of(
                new MarketPrice(
                        "Will Trump win 2024?",
                        BigDecimal.valueOf(0.52),  // цена YES
                        BigDecimal.valueOf(0.48),  // цена NO
                        "https://kalshi.com/markets/trump-2024"
                ),
                new MarketPrice(
                        "Will Biden drop out?",
                        BigDecimal.valueOf(0.32),
                        BigDecimal.valueOf(0.68),
                        "https://kalshi.com/markets/biden-drop-out"
                )
        );
    }

    /**
     * Поиск арбитражей между двумя площадками
     */
    private int findAndSaveArbitrages(List<MarketPrice> poly, List<MarketPrice> kalshi) {
        int count = 0;

        for (var polyMarket : poly) {
            for (var kalshiMarket : kalshi) {
                if (isSameEvent(polyMarket.title(), kalshiMarket.title())) {
                    log.debug("Сравнение события: {} vs {}", polyMarket.title(), kalshiMarket.title());

                    // Проверяем арбитраж: YES на Polymarket + NO на Kalshi
                    var arb1 = calculateArbitrage(polyMarket, kalshiMarket);
                    if (arb1 != null && isProfitable(arb1)) {
                        saveArbitrage(arb1);
                        count++;
                    }

                    // Проверяем арбитраж: NO на Polymarket + YES на Kalshi
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

    /**
     * Проверка, что это одно и то же событие
     */
    private boolean isSameEvent(String title1, String title2) {
        String t1 = title1.toLowerCase();
        String t2 = title2.toLowerCase();

        // Простое сравнение по ключевым словам
        return t1.contains(t2) || t2.contains(t1) ||
                extractKeywords(t1).equals(extractKeywords(t2));
    }

    private String extractKeywords(String title) {
        // Убираем стоп-слова и оставляем ключевые
        return title.toLowerCase()
                .replace("will", "")
                .replace("?", "")
                .replace("in", "")
                .replace("the", "")
                .trim();
    }

    /**
     * Расчет арбитража
     * @param platformYes площадка, где покупаем YES
     * @param platformNo площадка, где покупаем NO
     */
    private ArbitrageSignal calculateArbitrage(MarketPrice platformYes, MarketPrice platformNo) {
        // Сумма цен YES и NO должна быть меньше 1.0 для арбитража
        BigDecimal sum = platformYes.yesPrice().add(platformNo.noPrice());

        if (sum.compareTo(BigDecimal.ONE) >= 0) {
            log.debug("Нет арбитража: сумма = {}", sum);
            return null;
        }

        // Расчет прибыли: (1 / сумма) - 1
        BigDecimal profit = BigDecimal.ONE.divide(sum, 4, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(100));

        log.info("💰 Найден арбитраж! Прибыль: {}% (YES: {} @ {}, NO: {} @ {})",
                profit.setScale(2, RoundingMode.HALF_UP),
                platformYes.platform(),
                platformYes.yesPrice(),
                platformNo.platform(),
                platformNo.noPrice()
        );

        // Определяем названия площадок
        String platformYesName = platformYes.url().contains("polymarket") ? "Polymarket" : "Kalshi";
        String platformNoName = platformNo.url().contains("polymarket") ? "Polymarket" : "Kalshi";

        return ArbitrageSignal.builder()
                .eventTitle(platformYes.title())
                .platformYes(platformYesName)
                .platformNo(platformNoName)
                .priceYes(platformYes.yesPrice())
                .priceNo(platformNo.noPrice())
                .profitPercent(profit)
                .urlYes(platformYes.url())
                .urlNo(platformNo.url())
                .spreadPercent(BigDecimal.ONE.subtract(sum).abs().multiply(BigDecimal.valueOf(100)))
                .expectedProfit(profit)
                .status("NEW")
                .createdAt(OffsetDateTime.now())
                .build();
    }

    /**
     * Проверка, достаточно ли прибыльный арбитраж
     */
    private boolean isProfitable(ArbitrageSignal arb) {
        double minProfit = properties.getTrading().getMinProfitPercent();
        return arb.getProfitPercent().doubleValue() >= minProfit;
    }

    /**
     * Сохранение арбитража и отправка уведомления
     */
    private void saveArbitrage(ArbitrageSignal arb) {
        log.info("🎯 СОХРАНЕНИЕ АРБИТРАЖА: {} | Прибыль: {}%",
                arb.getEventTitle(),
                arb.getProfitPercent().setScale(2, RoundingMode.HALF_UP)
        );

        log.info("   📈 Купить YES: {} по цене {} | Ссылка: {}",
                arb.getPlatformYes(), arb.getPriceYes(), arb.getUrlYes()
        );
        log.info("   📉 Купить NO: {} по цене {} | Ссылка: {}",
                arb.getPlatformNo(), arb.getPriceNo(), arb.getUrlNo()
        );

        try {
            // Сохраняем в базу данных
            repository.save(arb);
            log.info("✅ Арбитраж сохранен в БД, ID: {}", arb.getId());

            // Отправляем уведомление в Telegram
            telegramBotService.notifyArbitrageFound(arb);
            log.info("📨 Уведомление отправлено в Telegram");

        } catch (Exception e) {
            log.error("❌ Ошибка при сохранении арбитража", e);
        }
    }

    /**
     * Вспомогательный класс для хранения цен с площадки
     */
    public record MarketPrice(
            String title,      // Название события
            BigDecimal yesPrice,  // Цена YES
            BigDecimal noPrice,   // Цена NO
            String url         // Ссылка на событие
    ) {
        public String platform() {
            return url.contains("polymarket") ? "Polymarket" : "Kalshi";
        }
    }
}