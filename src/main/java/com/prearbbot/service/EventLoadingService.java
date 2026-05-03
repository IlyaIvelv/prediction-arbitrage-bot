package com.prearbbot.service;

import com.prearbbot.core.model.RawEvent;
import com.prearbbot.core.repository.RawEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.util.List;

@Service
public class EventLoadingService {

    @Autowired
    private List<EventFetcher> fetchers;

    @Autowired
    private RawEventRepository repository;

    @Autowired
    private MatcherService matcherService;

    @Autowired
    private ArbitrageCalculatorService arbitrageService;

    @PostConstruct
    public void loadAllEvents() {
        System.out.println("🚀 Начинаю загрузку событий с бирж...");

        for (EventFetcher fetcher : fetchers) {
            List<RawEvent> events = fetcher.fetchEvents();
            repository.saveAll(events);
            System.out.println("✅ Загружено " + events.size() + " событий с " + fetcher.getExchangeName());
        }

        System.out.println("🎉 Загрузка завершена!");

        System.out.println("\n" + "=".repeat(50));
        matcherService.findAndSaveMatches();
        System.out.println("=".repeat(50));

        // Автоматический поиск арбитражей после загрузки
        arbitrageService.findArbitrageOpportunities(true);
    }
}