package com.prearbbot.service;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class EmbeddingService {

    // Пока заглушка — вместо реальной AI модели используем улучшенный TF-IDF
    // Позже заменим на настоящие эмбеддинги

    public double similarity(String text1, String text2) {
        String clean1 = clean(text1);
        String clean2 = clean(text2);

        // Улучшенное сравнение: учитываем ключевые слова
        Set<String> words1 = extractKeywords(clean1);
        Set<String> words2 = extractKeywords(clean2);

        if (words1.isEmpty() || words2.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);

        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);

        return (double) intersection.size() / union.size();
    }

    private String clean(String text) {
        if (text == null) return "";
        return text.toLowerCase()
                .replaceAll("\\?", "")
                .replaceAll("will|the|a|an|and|or|of|to|in|for|on|by|with|without|win|winner|champion", " ")
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Set<String> extractKeywords(String text) {
        return new HashSet<>(Arrays.asList(text.split(" ")));
    }
}