package com.prearbbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PolymarketEvent(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("markets") List<PolymarketMarket> markets
) {}