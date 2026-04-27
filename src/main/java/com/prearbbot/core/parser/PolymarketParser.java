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
public class PolymarketParser {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public PolymarketParser() {
        this.webClient = WebClient.builder()
                .baseUrl("https://clob.polymarket.com")
                .defaultHeader("User-Agent", "Mozilla/5.0")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public List<MarketPrice> fetchActiveMarkets() {
        log.info("📡 Начинаем парсинг Polymarket...");
        List<MarketPrice> markets = new ArrayList<>();

        try {
            String response = webClient.get()
                    .uri("/markets")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response != null) {
                JsonNode root = objectMapper.readTree(response);

                for (JsonNode market : root) {
                    String closed = market.has("closed") ? market.get("closed").asText() : "true";
                    if (closed.equals("true")) continue;

                    String question = market.has("question") ? market.get("question").asText() : "";
                    String marketId = market.has("id") ? market.get("id").asText() : "";
                    String conditionId = market.has("conditionId") ? market.get("conditionId").asText() : "";

                    if (question.isEmpty() || marketId.isEmpty()) continue;

                    // Реальный запрос цены
                    BigDecimal yesPrice = fetchYesPrice(marketId);
                    BigDecimal noPrice = BigDecimal.ONE.subtract(yesPrice);

                    String url = "https://polymarket.com/event/" + conditionId;

                    markets.add(new MarketPrice(
                            question,
                            yesPrice,
                            noPrice,
                            url,
                            "Polymarket"
                    ));

                    Thread.sleep(50);
                }

                log.info("✅ Загружено {} рынков с Polymarket", markets.size());
            }

        } catch (Exception e) {
            log.error("❌ Ошибка при парсинге Polymarket: {}", e.getMessage());
        }

        return markets;
    }

    private BigDecimal fetchYesPrice(String marketId) {
        try {
            String response = webClient.get()
                    .uri("/markets/{marketId}/prices", marketId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response != null) {
                JsonNode prices = objectMapper.readTree(response);
                if (prices.has("yes")) {
                    return BigDecimal.valueOf(prices.get("yes").asDouble())
                            .setScale(4, RoundingMode.HALF_UP);
                }
            }
        } catch (Exception e) {
            log.warn("Не удалось получить цену для marketId: {}", marketId);
        }
        return BigDecimal.ZERO;
    }
}