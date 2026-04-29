package com.prearbbot.scheduler;

import com.prearbbot.service.PolymarketSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketSyncScheduler {

    private final PolymarketSyncService syncService;

    @Scheduled(fixedRateString = "${sync.polymarket.interval:3600000}")
    public void runSync() {
        try {
            syncService.syncFromPolymarket();
        } catch (Exception e) {
            log.error("Ошибка при синхронизации Polymarket", e);
        }
    }
}