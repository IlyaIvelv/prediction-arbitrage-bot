package com.prearbbot.core.model;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
public class ArbitrageSignal {
    private Long id;
    private Long pairId;
    private String eventTitle;
    private String platformYes;
    private String platformNo;
    private BigDecimal priceYes;
    private BigDecimal priceNo;
    private BigDecimal profitPercent;
    private String urlYes;
    private String urlNo;
    private BigDecimal spreadPercent;
    private BigDecimal expectedProfit;
    private String status; // NEW, SENT, CONFIRMED, PLACED, EXPIRED, FAILED
    private OffsetDateTime createdAt;
    private OffsetDateTime expiresAt;
    private OffsetDateTime disappearedAt;
    private Long confirmedByChatId;
    private OffsetDateTime placedAt;
}