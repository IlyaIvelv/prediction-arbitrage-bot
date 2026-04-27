package com.prearbbot.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketPrice {
    private String title;      // Название события
    private BigDecimal yesPrice;  // Цена YES
    private BigDecimal noPrice;   // Цена NO
    private String url;        // Ссылка на событие
    private String platform;   // Название платформы
}