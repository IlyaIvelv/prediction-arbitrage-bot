package com.prearbbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prearbbot.core.model.RawEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

//@Component
public class SportmonksFetcher implements EventFetcher {

    @Value("${sportmonks.api.token:}")
    private String apiToken;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public List<RawEvent> fetchEvents() {
        List<RawEvent> events = new ArrayList<>();

        if (apiToken == null || apiToken.isEmpty()) {
            System.out.println("⚽ Sportmonks: API токен не настроен. Получите на https://sportmonks.com");
            return events;
        }

        try {
            System.out.println("⚽ Загружаю матчи с Sportmonks (API v2.0)...");

            String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            System.out.println("   📅 Сегодня: " + today);

            // Используем v2.0 эндпоинт для матчей за сегодня
            String url = "https://soccer.sportmonks.com/api/v2.0/fixtures/date/" + today
                    + "?api_token=" + apiToken
                    + "&include=localTeam,visitorTeam,odds";

            System.out.println("   🔗 Запрос к: " + url.replace(apiToken, "***"));

            String response = restTemplate.getForObject(url, String.class);

            if (response == null) {
                System.out.println("❌ Sportmonks: пустой ответ");
                return events;
            }

            // Проверяем на ошибку deprecation
            if (response.contains("deprecated") || response.contains("API 2 is deprecated")) {
                System.out.println("⚠️ API v2.0 устарел, пробуем v3...");
                return fetchEventsV3();
            }

            JsonNode root = mapper.readTree(response);
            JsonNode data = root.path("data");

            if (!data.isArray()) {
                System.out.println("⚠️ Sportmonks: ответ не содержит массива data");
                return events;
            }

            System.out.println("📊 Найдено матчей за сегодня: " + data.size());

            for (JsonNode match : data) {
                try {
                    String matchId = match.path("id").asText();
                    String homeTeam = match.path("localTeam").path("name").asText();
                    String awayTeam = match.path("visitorTeam").path("name").asText();
                    String title = homeTeam + " vs " + awayTeam;

                    // Парсим odds
                    JsonNode odds = match.path("odds");
                    BigDecimal homeWin = extractOdd(odds, "1");
                    BigDecimal awayWin = extractOdd(odds, "2");
                    BigDecimal draw = extractOdd(odds, "3");

                    // Если есть коэффициенты
                    if (homeWin != null && awayWin != null &&
                            homeWin.compareTo(BigDecimal.ZERO) > 0 &&
                            awayWin.compareTo(BigDecimal.ZERO) > 0) {

                        BigDecimal yesProb = BigDecimal.ONE.divide(homeWin, 4, RoundingMode.HALF_UP);
                        BigDecimal noProb = BigDecimal.ONE.divide(awayWin, 4, RoundingMode.HALF_UP);

                        events.add(new RawEvent(
                                "sportmonks",
                                matchId,
                                title,
                                yesProb,
                                noProb
                        ));
                        System.out.println("   ✅ " + title + " → YES(Home): " + yesProb + ", NO(Away): " + noProb);
                    }

                } catch (Exception e) {
                    System.err.println("   ⚠️ Ошибка обработки матча: " + e.getMessage());
                }
            }

            System.out.println("📊 Sportmonks: загружено " + events.size() + " событий");

        } catch (Exception e) {
            System.err.println("❌ Ошибка Sportmonks: " + e.getMessage());
            e.printStackTrace();
        }

        return events;
    }

    private List<RawEvent> fetchEventsV3() {
        List<RawEvent> events = new ArrayList<>();

        try {
            System.out.println("⚽ Пробую матчи через API v3 (live)...");

            String url = "https://api.sportmonks.com/v3/football/livescores?api_token=" + apiToken;

            String response = restTemplate.getForObject(url, String.class);

            if (response == null) {
                return events;
            }

            JsonNode root = mapper.readTree(response);
            JsonNode data = root.path("data");

            if (data.isArray() && !data.isEmpty()) {
                System.out.println("📊 Найдено live-матчей: " + data.size());

                for (JsonNode match : data) {
                    // Парсим v3 формат
                    String matchId = match.path("id").asText();
                    String name = match.path("name").asText();

                    events.add(new RawEvent(
                            "sportmonks",
                            matchId,
                            name,
                            new BigDecimal("0.50"),
                            new BigDecimal("0.50")
                    ));
                }
            } else {
                System.out.println("⚠️ Нет live-матчей или данные не найдены");
            }

        } catch (Exception e) {
            System.err.println("   Ошибка v3: " + e.getMessage());
        }

        return events;
    }

    private BigDecimal extractOdd(JsonNode odds, String field) {
        JsonNode node = odds.path(field);
        if (node.isMissingNode()) return null;
        try {
            String value = node.asText();
            if (value == null || value.isEmpty()) return null;
            return new BigDecimal(value);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getExchangeName() {
        return "sportmonks";
    }
}