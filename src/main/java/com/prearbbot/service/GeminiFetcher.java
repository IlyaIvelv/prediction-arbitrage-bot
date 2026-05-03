package com.prearbbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prearbbot.core.model.RawEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class GeminiFetcher implements EventFetcher {

    private static final String GEMINI_API_URL = "https://api.gemini.com/v1/prediction-markets/events";

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public List<RawEvent> fetchEvents() {
        List<RawEvent> events = new ArrayList<>();

        try {
            System.out.println("🔮 Загружаю события с Gemini Prediction Markets...");

            // Получаем активные события
            String url = GEMINI_API_URL + "?status=active&limit=50";

            String response = restTemplate.getForObject(url, String.class);

            if (response == null || response.isEmpty()) {
                System.out.println("❌ Gemini: пустой ответ");
                return events;
            }

            JsonNode root = mapper.readTree(response);
            JsonNode data = root.path("data");

            if (!data.isArray()) {
                System.out.println("⚠️ Gemini: ответ не содержит массива data");
                return events;
            }

            System.out.println("📊 Найдено событий: " + data.size());

            for (JsonNode event : data) {
                String eventTitle = event.path("title").asText();
                JsonNode contracts = event.path("contracts");

                if (eventTitle == null || eventTitle.isEmpty()) {
                    continue;
                }

                // Идем по контрактам каждого события
                for (JsonNode contract : contracts) {
                    String contractLabel = contract.path("label").asText();
                    JsonNode prices = contract.path("prices");

                    // Gemini дает ask цену (цена покупки) и bid цену (цена продажи)
                    String bestAskStr = prices.path("bestAsk").asText();
                    String bestBidStr = prices.path("bestBid").asText();

                    if (bestAskStr == null || bestAskStr.isEmpty() || bestBidStr == null || bestBidStr.isEmpty()) {
                        continue;
                    }

                    BigDecimal askPrice = new BigDecimal(bestAskStr);
                    BigDecimal bidPrice = new BigDecimal(bestBidStr);

                    // Для арбитража используем ask цену (покупка YES и NO)
                    // У одного контракта YES и NO — это две стороны одного рынка
                    // Но Gemini хранит их как отдельные контракты
                    // Нужно найти пару (YES/NO) для одного вопроса

                    // Определяем тип контракта по label
                    if (contractLabel != null && !contractLabel.isEmpty()) {
                        // Пока просто добавляем все контракты с их ценами
                        // Для демо используем ask цену как вероятность

                        events.add(new RawEvent(
                                "gemini",
                                contract.path("instrumentSymbol").asText(),
                                eventTitle + " → " + contractLabel,
                                askPrice,
                                BigDecimal.ONE.subtract(askPrice)
                        ));
                        System.out.println("   ✅ " + eventTitle + " → " + contractLabel + ": " + askPrice);
                    }
                }
            }

            System.out.println("📊 Gemini: загружено " + events.size() + " контрактов");

        } catch (Exception e) {
            System.err.println("❌ Ошибка Gemini: " + e.getMessage());
            e.printStackTrace();
        }

        return events;
    }

    @Override
    public String getExchangeName() {
        return "gemini";
    }
}