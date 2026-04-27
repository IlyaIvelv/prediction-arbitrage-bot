package com.prearbbot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanningStatusService {

    private final ApplicationEventPublisher eventPublisher;
    private final AtomicBoolean scanningEnabled = new AtomicBoolean(true);

    public void start() {
        scanningEnabled.set(true);
        log.info("Сканирование включено");
    }

    public void stop() {
        scanningEnabled.set(false);
        log.info("Сканирование выключено");
    }

    public boolean isEnabled() {
        return scanningEnabled.get();
    }

    public String getStatus() {
        return scanningEnabled.get() ? "🟢 РАБОТАЕТ" : "🔴 ОСТАНОВЛЕН";
    }

    public void triggerManualScan() {
        eventPublisher.publishEvent(new ManualScanEvent(this));
    }

    public static class ManualScanEvent {
        private final Object source;
        public ManualScanEvent(Object source) { this.source = source; }
        public Object getSource() { return source; }
    }
}