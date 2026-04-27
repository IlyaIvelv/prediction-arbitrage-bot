package com.prearbbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class TelegramBotControlService {
    private final AtomicBoolean scanningEnabled = new AtomicBoolean(true);

    public boolean startScanning() {
        if (scanningEnabled.get()) return false;
        scanningEnabled.set(true);
        log.info("✅ Сканирование запущено");
        return true;
    }

    public boolean stopScanning() {
        if (!scanningEnabled.get()) return false;
        scanningEnabled.set(false);
        log.info("⏹️ Сканирование остановлено");
        return true;
    }

    public boolean isScanningEnabled() {
        return scanningEnabled.get();
    }

    public String getStatus() {
        return scanningEnabled.get() ? "🟢 РАБОТАЕТ" : "🔴 ОСТАНОВЛЕН";
    }
}