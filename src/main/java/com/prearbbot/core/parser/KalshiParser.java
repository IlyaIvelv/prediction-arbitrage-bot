package com.prearbbot.core.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prearbbot.core.model.MarketPrice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class KalshiParser {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public KalshiParser() {
        this.webClient = WebClient.builder()
                .baseUrl("https://www.kalshi.com/api")
                .defaultHeader("User-Agent", "Mozilla/5.0")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public List<MarketPrice> fetchActiveEvents() {
        log.info("📡 Начинаем парсинг Kalshi...");
        List<MarketPrice> events = new ArrayList<>();

        try {
            String response = webClient.get()
                    .uri("/markets")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                JsonNode marketsArray = root.path("markets");

                for (JsonNode market : marketsArray) {
                    String status = market.path("status").asText();
                    if (!"active".equals(status)) continue;

                    String title = market.path("title").asText();
                    String marketTicker = market.path("ticker").asText();

                    if (title.isEmpty()) continue;

                    // Реальный запрос цены через order book
                    BigDecimal yesPrice = fetchYesPrice(marketTicker);
                    BigDecimal noPrice = BigDecimal.ONE.subtract(yesPrice);

                    String url = "https://kalshi.com/markets/" + marketTicker;

                    events.add(new MarketPrice(
                            title,
                            yesPrice,
                            noPrice,
                            url,
                            "Kalshi"
                    ));

                    Thread.sleep(50);
                }

                log.info("✅ Загружено {} рынков с Kalshi", events.size());
            }

        } catch (Exception e) {
            log.error("❌ Ошибка при парсинге Kalshi: {}", e.getMessage());
        }

        return events;
    }

    private BigDecimal fetchYesPrice(String marketTicker) {
        try {
            String response = webClient.get()
                    .uri("/markets/{marketTicker}/book", marketTicker)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                JsonNode bids = root.path("bids");

                if (bids.isArray() && bids.size() > 0) {
                    double bestBid = bids.get(0).path("price").asDouble();
                    return BigDecimal.valueOf(bestBid).setScale(4, RoundingMode.HALF_UP);
                }
            }
        } catch (Exception e) {
            log.warn("Не удалось получить цену для market: {}", marketTicker);
        }
        return BigDecimal.ZERO;
    }
}