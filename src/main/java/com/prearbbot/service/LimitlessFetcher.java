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

//@Component
public class LimitlessFetcher implements EventFetcher {

    // Публичный GraphQL эндпоинт сабграфа (без ключа, без rate limits)
    private static final String SUBGRAPH_URL = "https://api.studio.thegraph.com/query/94776/limitless-simple-markets/v0.0.5";

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public List<RawEvent> fetchEvents() {
        List<RawEvent> events = new ArrayList<>();

        try {
            System.out.println("♾️ Загружаю активные рынки с Limitless (через GraphQL)...");

            // GraphQL запрос для получения активных рынков с ценами
            String query = """
                {
                  markets(first: 100, where: { resolved: false, volumeUSD_gt: 0 }) {
                    id
                    conditionId
                    questionId
                    title
                    description
                    volumeUSD
                    tradesCount
                    outcomePrices
                    outcomes
                    liquidity
                    expiryDate
                  }
                }
                """;

            // Формируем POST запрос с GraphQL
            String requestBody = "{\"query\": \"" + query.replace("\"", "\\\"").replace("\n", " ") + "\"}";

            // Отправляем запрос
            String response = restTemplate.postForObject(SUBGRAPH_URL, requestBody, String.class);

            if (response == null || response.isEmpty()) {
                System.out.println("❌ Limitless: пустой ответ");
                return events;
            }

            JsonNode root = mapper.readTree(response);
            JsonNode data = root.path("data");
            JsonNode markets = data.path("markets");

            if (!markets.isArray()) {
                System.out.println("⚠️ Limitless: ответ не содержит массива markets");
                System.out.println("   Ответ: " + (response.length() > 300 ? response.substring(0, 300) : response));
                return events;
            }

            System.out.println("📊 Найдено активных рынков: " + markets.size());

            int loaded = 0;
            for (JsonNode market : markets) {
                try {
                    String marketId = market.path("id").asText();
                    String title = market.path("title").asText();

                    if (title == null || title.isEmpty()) {
                        continue;
                    }

                    // Парсим outcomePrices из строки JSON
                    String outcomePricesStr = market.path("outcomePrices").asText();
                    String outcomesStr = market.path("outcomes").asText();

                    BigDecimal yesPrice = null;
                    BigDecimal noPrice = null;

                    // Если есть outcomePrices в виде JSON массива
                    if (outcomePricesStr != null && !outcomePricesStr.isEmpty()) {
                        JsonNode pricesArray = mapper.readTree(outcomePricesStr);
                        JsonNode outcomesArray = mapper.readTree(outcomesStr);

                        if (pricesArray.isArray() && outcomesArray.isArray()) {
                            for (int i = 0; i < pricesArray.size() && i < outcomesArray.size(); i++) {
                                String outcome = outcomesArray.get(i).asText();
                                BigDecimal price = new BigDecimal(pricesArray.get(i).asText());

                                if ("Yes".equalsIgnoreCase(outcome)) {
                                    yesPrice = price;
                                } else if ("No".equalsIgnoreCase(outcome)) {
                                    noPrice = price;
                                }
                            }
                        }
                    }

                    if (yesPrice != null && noPrice != null &&
                            yesPrice.compareTo(BigDecimal.ZERO) > 0 &&
                            noPrice.compareTo(BigDecimal.ZERO) > 0) {

                        events.add(new RawEvent(
                                "limitless",
                                marketId,
                                title,
                                yesPrice,
                                noPrice
                        ));
                        System.out.println("   ✅ " + title + " → YES: " + yesPrice + ", NO: " + noPrice);
                        loaded++;
                    } else {
                        System.out.println("   🔍 Пропущено (нет цен): " + title);
                    }

                    if (loaded >= 30) break; // Ограничим для скорости

                } catch (Exception e) {
                    System.err.println("   ⚠️ Ошибка обработки рынка: " + e.getMessage());
                }
            }

            System.out.println("📊 Limitless (через GraphQL): загружено " + events.size() + " событий с ценами");

        } catch (Exception e) {
            System.err.println("❌ Ошибка Limitless: " + e.getMessage());
            e.printStackTrace();
        }

        return events;
    }

    @Override
    public String getExchangeName() {
        return "limitless";
    }
}