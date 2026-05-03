package com.prearbbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prearbbot.core.model.RawEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// ВРЕМЕННО ОТКЛЮЧАЕМ
// @Component
public class KalshiFetcher implements EventFetcher {

    private static final String KALSHI_API_URL = "https://api.elections.kalshi.com/trade-api/v2/markets?limit=50";

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public KalshiFetcher(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public List<RawEvent> fetchEvents() {
        List<RawEvent> events = new ArrayList<>();
        try {
            System.out.println("🟠 Загружаю события с Kalshi...");
            String response = restTemplate.getForObject(KALSHI_API_URL, String.class);

            if (response == null || response.isEmpty()) {
                System.out.println("❌ Kalshi: пустой ответ");
                return events;
            }

            JsonNode root = mapper.readTree(response);
            JsonNode markets = root.path("markets");

            for (JsonNode market : markets) {
                String ticker = market.path("ticker").asText();
                String title = market.path("title").asText();
                String subtitle = market.path("subtitle").asText();
                String fullTitle = title + (subtitle.isEmpty() ? "" : " - " + subtitle);

                BigDecimal yesPrice = parsePrice(market.path("yes_ask"));
                BigDecimal noPrice = parsePrice(market.path("no_ask"));

                if (yesPrice == null || yesPrice.compareTo(BigDecimal.ZERO) == 0) {
                    yesPrice = parsePrice(market.path("yes_bid"));
                }
                if (noPrice == null || noPrice.compareTo(BigDecimal.ZERO) == 0) {
                    noPrice = parsePrice(market.path("no_bid"));
                }

                if (yesPrice != null && noPrice != null &&
                        yesPrice.compareTo(BigDecimal.ZERO) > 0 &&
                        noPrice.compareTo(BigDecimal.ZERO) > 0) {

                    events.add(new RawEvent("kalshi", ticker, fullTitle, yesPrice, noPrice));
                    System.out.println("   ✅ " + fullTitle + " → YES: " + yesPrice + ", NO: " + noPrice);
                }
            }

            System.out.println("📊 Kalshi: загружено " + events.size() + " событий (с ненулевыми ценами)");

        } catch (Exception e) {
            System.err.println("❌ Ошибка Kalshi: " + e.getMessage());
            e.printStackTrace();
        }
        return events;
    }

    private BigDecimal parsePrice(JsonNode priceNode) {
        if (priceNode == null || priceNode.isMissingNode()) return null;
        try {
            String priceStr = priceNode.asText();
            if (priceStr == null || priceStr.isEmpty() || "0.0000".equals(priceStr)) return null;
            return new BigDecimal(priceStr);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getExchangeName() {
        return "kalshi";
    }
}