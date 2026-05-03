package com.prearbbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prearbbot.core.model.RawEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;

@Component
public class BetfairFetcher implements EventFetcher {

    @Value("${betfair.api.app-key}")
    private String appKey;

    @Value("${betfair.api.username}")
    private String username;

    @Value("${betfair.api.password}")
    private String password;

    @Value("${betfair.api.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    private String sessionToken;

    @Override
    public List<RawEvent> fetchEvents() {
        List<RawEvent> events = new ArrayList<>();

        try {
            System.out.println("🎲 Загружаю события с Betfair...");

            // Аутентификация
            if (!login()) {
                System.out.println("❌ Betfair: не удалось аутентифицироваться");
                return events;
            }

            // Получаем футбольные события
            HttpHeaders headers = createHeaders();

            // 1. Получаем список матчей
            Map<String, Object> catalogRequest = Map.of(
                    "filter", Map.of(
                            "marketTypeCodes", List.of("MATCH_ODDS"),
                            "eventTypeIds", List.of("1") // 1 = футбол
                    ),
                    "maxResults", 100,
                    "marketProjection", List.of("MARKET_NAME", "EVENT", "RUNNER_DESCRIPTION")
            );

            String catalogResponse = postRequest(baseUrl + "/listMarketCatalogue/", catalogRequest, headers);
            JsonNode catalogRoot = mapper.readTree(catalogResponse);

            System.out.println("📊 Найдено рынков: " + catalogRoot.size());

            for (JsonNode market : catalogRoot) {
                String marketId = market.path("marketId").asText();
                String marketName = market.path("marketName").asText();
                String eventName = market.path("event").path("name").asText();
                String fullTitle = eventName + " - " + marketName;

                // 2. Получаем цены
                Map<String, Object> bookRequest = Map.of(
                        "marketIds", List.of(marketId),
                        "priceProjection", Map.of("priceData", List.of("EX_BEST_OFFERS"))
                );

                String bookResponse = postRequest(baseUrl + "/listMarketBook/", bookRequest, headers);
                JsonNode bookRoot = mapper.readTree(bookResponse);

                if (bookRoot.isArray() && bookRoot.size() > 0) {
                    JsonNode marketBook = bookRoot.get(0);
                    JsonNode runners = marketBook.path("runners");

                    // Ищем лучшую цену на Back (покупка) и Lay (продажа)
                    // Для арбитража нам нужны Back цены
                    for (JsonNode runner : runners) {
                        String runnerName = runner.path("runnerName").asText();
                        JsonNode bestBack = runner.path("exchangePrices").path("availableToBack");

                        if (bestBack.isArray() && bestBack.size() > 0) {
                            BigDecimal price = new BigDecimal(bestBack.get(0).path("price").asText());
                            BigDecimal prob = BigDecimal.ONE.divide(price, 4, BigDecimal.ROUND_HALF_UP);

                            // У Betfair нет явных YES/NO, это контракты на каждого участника
                            // Для демо добавляем как YES (победа команды 1) и NO (победа команды 2)
                            events.add(new RawEvent(
                                    "betfair",
                                    marketId,
                                    fullTitle + " → " + runnerName,
                                    prob,
                                    BigDecimal.ONE.subtract(prob)
                            ));
                            System.out.println("   ✅ " + fullTitle + " → " + runnerName + ": " + price);
                        }
                    }
                }

                Thread.sleep(200);
            }

            System.out.println("📊 Betfair: загружено " + events.size() + " исходов");

        } catch (Exception e) {
            System.err.println("❌ Ошибка Betfair: " + e.getMessage());
            e.printStackTrace();
        }

        return events;
    }

    private boolean login() {
        try {
            System.out.println("🔐 Аутентификация на Betfair...");

            String url = "https://identitysso.betfair.com/api/certlogin";

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Application", appKey);
            headers.set("Content-Type", "application/x-www-form-urlencoded");

            String body = "username=" + username + "&password=" + password;

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            JsonNode root = mapper.readTree(response.getBody());

            if (root.has("status") && "SUCCESS".equals(root.get("status").asText())) {
                sessionToken = root.get("token").asText();
                System.out.println("✅ Аутентификация успешна");
                return true;
            } else {
                System.err.println("❌ Ошибка аутентификации: " + (root.has("error") ? root.get("error").asText() : response.getBody()));
                return false;
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка логина: " + e.getMessage());
            return false;
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Application", appKey);
        headers.set("X-Authentication", sessionToken);
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private String postRequest(String url, Map<String, Object> body, HttpHeaders headers) {
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        return response.getBody();
    }

    @Override
    public String getExchangeName() {
        return "betfair";
    }
}