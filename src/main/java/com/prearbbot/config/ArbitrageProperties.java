package com.prearbbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "arbitrage")
public class ArbitrageProperties {
    private boolean dryRun = true;
    private String scanInterval = "30s";
    private Trading trading = new Trading();
    private AutoTrade autoTrade = new AutoTrade();
    private List<PlatformConfig> platforms = new ArrayList<>();

    @Data
    public static class Trading {
        private double betAmount = 10.0;
        private double minProfitPercent = 3.0;
        private int maxOpenPositions = 5;
    }

    @Data
    public static class AutoTrade {
        private boolean enabled = false;
        private String defaultAccount = "polymarket";
    }

    @Data
    public static class PlatformConfig {
        private String id;
        private boolean enabled = true;
        private String apiUrl;
        private String walletAddress;
        private String privateKey;
        private String apiKey;
        private String apiSecret;
    }
}