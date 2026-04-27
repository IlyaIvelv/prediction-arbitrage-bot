package com.prearbbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "telegram")
public class TelegramProperties {
    private String token;
    private List<Long> chatIds = new ArrayList<>();
    private boolean autoConfirmEnabled = false;
    private int confirmationTimeoutSec = 30;
}