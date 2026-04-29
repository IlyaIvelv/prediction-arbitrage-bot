package com.prearbbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record PolymarketMarket(
        @JsonProperty("id") String marketId,
        @JsonProperty("outcomes") String outcomesJson,
        @JsonProperty("outcomePrices") String pricesJson,
        @JsonProperty("volumeNum") BigDecimal volume,
        @JsonProperty("liquidityNum") BigDecimal liquidity
) {
    public List<Outcome> parseOutcomes() {
        try {
            var mapper = new com.fasterxml.jackson.databind.json.JsonMapper();
            String[] labels = mapper.readValue(outcomesJson, String[].class);
            BigDecimal[] prices = mapper.readValue(pricesJson, BigDecimal[].class);

            var result = new java.util.ArrayList<Outcome>();
            for (int i = 0; i < Math.min(labels.length, prices.length); i++) {
                result.add(new Outcome(labels[i], prices[i]));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }
    public record Outcome(String label, BigDecimal price) {}
}