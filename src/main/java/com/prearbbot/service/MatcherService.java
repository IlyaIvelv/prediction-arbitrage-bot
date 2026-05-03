package com.prearbbot.service;

import com.prearbbot.core.model.RawEvent;
import com.prearbbot.core.repository.RawEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MatcherService {

    @Autowired
    private RawEventRepository rawEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Стоп-слова, которые не влияют на смысл
    private static final Set<String> STOP_WORDS = Set.of(
            "will", "the", "a", "an", "and", "or", "of", "to", "in", "for", "on", "by", "with", "without",
            "win", "winner", "champion", "finals", "president", "election", "before", "after", "during",
            "2026", "2025", "2027", "2028", "vs", "verus", "fc", "united", "city"
    );

    public void findAndSaveMatches() {
        System.out.println("\n🔗 ЗАПУСК МАТЧЕРА: поиск одинаковых событий между биржами...");

        List<RawEvent> allEvents = rawEventRepository.findAll();
        if (allEvents.isEmpty()) {
            System.out.println("⚠️ Нет событий для матчинга. Сначала загрузи данные.");
            return;
        }

        // Группируем события по биржам
        Map<String, List<RawEvent>> eventsByExchange = allEvents.stream()
                .collect(Collectors.groupingBy(RawEvent::getExchange));

        List<RawEvent> geminiEvents = eventsByExchange.getOrDefault("gemini", List.of());
        List<RawEvent> polymarketEvents = eventsByExchange.getOrDefault("polymarket", List.of());

        System.out.println("📊 Сравниваю Gemini (" + geminiEvents.size() + ") с Polymarket (" + polymarketEvents.size() + ")");

        //TODO переделать это
        // Очищаем старые связи
        jdbcTemplate.update("DELETE FROM arbitrage_opportunities");
        jdbcTemplate.update("DELETE FROM event_matches");


        int matchesFound = 0;
        for (RawEvent gemini : geminiEvents) {
            String cleanGemini = cleanText(gemini.getTitle());

            for (RawEvent polymarket : polymarketEvents) {
                String cleanPoly = cleanText(polymarket.getTitle());
                double similarity = calculateCosineSimilarity(cleanGemini, cleanPoly);

                // Если похожи на 70% и больше — считаем, что это одно событие
                if (similarity >= 0.35) {
                    saveMatch(gemini, polymarket, similarity);
                    matchesFound++;
                    System.out.println("   ✅ НАЙДЕНО! Схожесть " + String.format("%.2f", similarity * 100) + "%");
                    System.out.println("      Gemini: " + gemini.getTitle());
                    System.out.println("      Polymarket: " + polymarket.getTitle());
                    break; // Нашли пару для этого Gemini, переходим к следующему
                }
            }
        }

        System.out.println("🎯 Матчинг завершён. Найдено связок: " + matchesFound);
    }

    private String cleanText(String text) {
        if (text == null) return "";
        return text.toLowerCase()
                .replaceAll("\\?", "")
                .replaceAll("[^a-zа-я0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private double calculateCosineSimilarity(String text1, String text2) {
        Set<String> words1 = Arrays.stream(text1.split(" ")).filter(w -> !STOP_WORDS.contains(w)).collect(Collectors.toSet());
        Set<String> words2 = Arrays.stream(text2.split(" ")).filter(w -> !STOP_WORDS.contains(w)).collect(Collectors.toSet());

        if (words1.isEmpty() || words2.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);

        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);

        return (double) intersection.size() / union.size();
    }

    private void saveMatch(RawEvent gemini, RawEvent polymarket, double similarity) {
        String sql = "INSERT INTO event_matches (event_id_1, event_id_2, similarity_score) VALUES (?, ?, ?)";
        jdbcTemplate.update(sql, gemini.getId(), polymarket.getId(), similarity);
    }
}