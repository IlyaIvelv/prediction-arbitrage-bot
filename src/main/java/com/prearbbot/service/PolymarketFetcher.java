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

@Component
public class PolymarketFetcher implements EventFetcher {

    private static final String GAMMA_API_URL = "https://gamma-api.polymarket.com/markets";

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public List<RawEvent> fetchEvents() {
        List<RawEvent> events = new ArrayList<>();

        try {
            System.out.println("🔵 Загружаю рынки с Polymarket...");
            String url = GAMMA_API_URL + "?active=true&closed=false&limit=100";
            String response = restTemplate.getForObject(url, String.class);

            if (response == null || response.isEmpty()) {
                System.out.println("❌ Polymarket: пустой ответ");
                return events;
            }

            JsonNode root = mapper.readTree(response);
            if (!root.isArray()) {
                System.out.println("⚠️ Polymarket: ответ не массив");
                return events;
            }

            System.out.println("🔵 Polymarket: найдено рынков: " + root.size());

            for (JsonNode market : root) {
                try {
                    // Получаем вопрос и ID
                    String marketId = market.path("id").asText();
                    String question = market.path("question").asText();

                    if (question == null || question.isEmpty()) {
                        continue;
                    }

                    // Извлекаем строку с ценами и парсим её как JSON-массив
                    JsonNode outcomePricesNode = market.path("outcomePrices");
                    if (outcomePricesNode.isMissingNode() || outcomePricesNode.asText().isEmpty()) {
                        continue;
                    }

                    // outcomePricesNode.asText() вернёт строку типа "[\"0.555\", \"0.445\"]"
                    String pricesStr = outcomePricesNode.asText();
                    JsonNode pricesArray = mapper.readTree(pricesStr);

                    if (!pricesArray.isArray() || pricesArray.size() < 2) {
                        continue;
                    }

                    // Предполагаем, что первый элемент — это цена Yes, второй — No
                    BigDecimal yesPrice = new BigDecimal(pricesArray.get(0).asText());
                    BigDecimal noPrice = new BigDecimal(pricesArray.get(1).asText());

                    // Добавляем событие, если обе цены корректные
                    if (yesPrice.compareTo(BigDecimal.ZERO) > 0 && noPrice.compareTo(BigDecimal.ZERO) > 0) {
                        events.add(new RawEvent(
                                "polymarket",
                                marketId,
                                question,
                                yesPrice,
                                noPrice
                        ));
                        System.out.println("   ✅ " + question + " → YES: " + yesPrice + ", NO: " + noPrice);
                    } else {
                        System.out.println("   ⚠️ Пропущено (нулевые цены): " + question);
                    }

                } catch (Exception e) {
                    System.err.println("   ⚠️ Ошибка обработки рынка: " + e.getMessage());
                }
            }

            System.out.println("📊 Polymarket: загружено " + events.size() + " событий с ценами");

        } catch (Exception e) {
            System.err.println("❌ Ошибка Polymarket: " + e.getMessage());
            e.printStackTrace();
        }

        return events;
    }

    @Override
    public String getExchangeName() {
        return "polymarket";
    }
}