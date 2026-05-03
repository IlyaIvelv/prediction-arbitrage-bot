package com.prearbbot.client;

import com.prearbbot.dto.PolymarketEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PolymarketClient {


    public List<PolymarketEvent> fetchAllEvents() {
        // TODO: реализовать вызов API Polymarket
        // Пока вернём пустой список
        return List.of();
    }
    
//    private final RestClient restClient;
//
//    public List<PolymarketEvent> fetchAllEvents() {
//        List<PolymarketEvent> allEvents = new ArrayList<>();
//        int offset = 0;
//        final int limit = 10;  // ← УМЕНЬШИЛИ с 500 до 100
//
//        while (true) {
//            final int currentOffset = offset;
//
//            var batch = restClient.get()
//                    .uri(uriBuilder -> uriBuilder
//                            .path("/events")
//                            .queryParam("active", true)
//                            .queryParam("limit", limit)  // ← 100 вместо 500
//                            .queryParam("offset", currentOffset)
//                            .build())
//                    .retrieve()
//                    .body(new ParameterizedTypeReference<List<PolymarketEvent>>() {});
//
//            if (batch == null || batch.isEmpty()) break;
//            allEvents.addAll(batch);
//            offset += limit;
//            if (batch.size() < limit) break;
//        }
//
//        log.info("📥 Загружено всего {} событий с Polymarket", allEvents.size());
//        return allEvents;
//    }
}