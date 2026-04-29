package com.prearbbot.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.prearbbot.client.PolymarketClient;
import com.prearbbot.dto.PolymarketEvent;
import com.prearbbot.dto.PolymarketMarket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.prearbbot.generated.tables.Markets.MARKETS;
import static com.prearbbot.generated.tables.Outcomes.OUTCOMES;
import static com.prearbbot.generated.tables.Platforms.PLATFORMS;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolymarketSyncService {

    private final PolymarketClient client;
    private final DSLContext dsl;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Transactional
    public void syncFromPolymarket() {
        List<PolymarketEvent> events = client.fetchAllEvents();
        if (events == null || events.isEmpty()) {
            log.info("Polymarket вернул 0 событий. Синхронизация пропущена.");
            return;
        }

        UUID platformId = ensurePlatform("Polymarket");
        OffsetDateTime now = OffsetDateTime.now();
        // Заглушка: события без даты истекают через 30 дней
        OffsetDateTime defaultExpiration = now.plusDays(30);
        int processedCount = 0;
        int skippedOutcomes = 0;

        for (PolymarketEvent event : events) {
            if (event.id() == null || event.title() == null) {
                log.warn("⚠️ Пропущено событие с null id/title: {}", event);
                continue;
            }

            String sourceUrl = "https://polymarket.com/event/" + event.id();

            // Вставка/обновление маркета (порядок колонок как в БД!)
            UUID marketId = dsl.insertInto(MARKETS)
                    .columns(
                            MARKETS.ID,
                            MARKETS.EXTERNAL_ID,      // String
                            MARKETS.PLATFORM_ID,      // UUID
                            MARKETS.TITLE,
                            MARKETS.STATUS,
                            MARKETS.SOURCE_URL,
                            MARKETS.EXPIRATION_DATE,
                            MARKETS.CREATED_AT,
                            MARKETS.UPDATED_AT
                    )
                    .values(
                            UUID.randomUUID(),
                            event.id(),                // external_id
                            platformId,                // platform_id
                            event.title(),
                            "ACTIVE",
                            sourceUrl,
                            defaultExpiration,
                            now,
                            now
                    )
                    .onConflict(MARKETS.EXTERNAL_ID, MARKETS.PLATFORM_ID)
                    .doUpdate()
                    .set(MARKETS.TITLE, event.title())
                    .set(MARKETS.SOURCE_URL, sourceUrl)
                    .set(MARKETS.EXPIRATION_DATE, defaultExpiration)
                    .set(MARKETS.UPDATED_AT, now)
                    .returning(MARKETS.ID)
                    .fetchOne()
                    .getValue(MARKETS.ID);

            // Обработка исходов
            if (event.markets() != null) {
                for (PolymarketMarket market : event.markets()) {
                    // Парсим исходы из JSON-строк
                    var outcomes = parseOutcomes(market);
                    if (outcomes.isEmpty()) {
                        log.warn("⚠️ Не удалось распарсить исходы для market_id={}", market.marketId());
                        skippedOutcomes++;
                        continue;
                    }

                    for (var outcome : outcomes) {
                        if (outcome.label() == null || outcome.label().isBlank()) {
                            log.warn("⚠️ Пропущен исход с null label. market_id={}", market.marketId());
                            skippedOutcomes++;
                            continue;
                        }
                        if (outcome.price() < 0 || outcome.price() > 1) {
                            log.warn("⚠️ Пропущен исход с невалидной ценой: {}", outcome.price());
                            skippedOutcomes++;
                            continue;
                        }

                        dsl.insertInto(OUTCOMES)
                                .columns(
                                        OUTCOMES.ID,
                                        OUTCOMES.MARKET_ID,
                                        OUTCOMES.LABEL,
                                        OUTCOMES.PRICE,
                                        OUTCOMES.UPDATED_AT
                                )
                                .values(
                                        UUID.randomUUID(),
                                        marketId,
                                        outcome.label(),
                                        BigDecimal.valueOf(outcome.price()),
                                        now
                                )
                                .onConflict(OUTCOMES.MARKET_ID, OUTCOMES.LABEL)
                                .doUpdate()
                                .set(OUTCOMES.PRICE, BigDecimal.valueOf(outcome.price()))
                                .set(OUTCOMES.UPDATED_AT, now)
                                .execute();
                    }
                }
            }
            processedCount++;
        }

        log.info("✅ Успешно обработано {} событий из Polymarket (пропущено исходов: {})",
                processedCount, skippedOutcomes);
    }

    /**
     * Парсит исходы из JSON-строк outcomesJson и outcomePrices
     */
    private List<Outcome> parseOutcomes(PolymarketMarket market) {
        try {
            if (market.outcomesJson() == null || market.pricesJson() == null) {
                return List.of();
            }

            String[] labels = jsonMapper.readValue(market.outcomesJson(), String[].class);
            Double[] prices = jsonMapper.readValue(market.pricesJson(), Double[].class);

            var result = new java.util.ArrayList<Outcome>();
            int len = Math.min(labels.length, prices.length);
            for (int i = 0; i < len; i++) {
                result.add(new Outcome(labels[i], prices[i]));
            }
            return result;
        } catch (Exception e) {
            log.warn("⚠️ Ошибка парсинга исходов для market_id={}: {}", market.marketId(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Простой рекорд для исхода
     */
    private record Outcome(String label, double price) {}

    /**
     * Гарантирует наличие платформы в БД
     */
    private UUID ensurePlatform(String name) {
        return dsl.insertInto(PLATFORMS)
                .columns(PLATFORMS.ID, PLATFORMS.NAME, PLATFORMS.IS_ACTIVE, PLATFORMS.CREATED_AT)
                .values(UUID.randomUUID(), name, true, OffsetDateTime.now())
                .onConflict(PLATFORMS.NAME)
                .doUpdate()
                .set(PLATFORMS.IS_ACTIVE, true)  // ← no-op update, просто чтобы вернуть строку
                .returning(PLATFORMS.ID)
                .fetchOne()
                .getValue(PLATFORMS.ID);
    }
}