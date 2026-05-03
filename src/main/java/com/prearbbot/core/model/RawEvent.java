package com.prearbbot.core.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class RawEvent {
    private Long id;
    private String exchange;
    private String externalId;
    private String title;
    private BigDecimal yesPrice;
    private BigDecimal noPrice;
    private LocalDateTime fetchedAt;

    public RawEvent(String exchange, String externalId, String title,
                    BigDecimal yesPrice, BigDecimal noPrice) {
        this.exchange = exchange;
        this.externalId = externalId;
        this.title = title;
        this.yesPrice = yesPrice;
        this.noPrice = noPrice;
        this.fetchedAt = LocalDateTime.now();
    }

    public RawEvent(Long id, String exchange, String externalId, String title,
                    BigDecimal yesPrice, BigDecimal noPrice, LocalDateTime fetchedAt) {
        this.id = id;
        this.exchange = exchange;
        this.externalId = externalId;
        this.title = title;
        this.yesPrice = yesPrice;
        this.noPrice = noPrice;
        this.fetchedAt = fetchedAt;
    }

    public Long getId() { return id; }
    public String getExchange() { return exchange; }
    public String getExternalId() { return externalId; }
    public String getTitle() { return title; }
    public BigDecimal getYesPrice() { return yesPrice; }
    public BigDecimal getNoPrice() { return noPrice; }
    public LocalDateTime getFetchedAt() { return fetchedAt; }

    public void setId(Long id) { this.id = id; }
    public void setExchange(String exchange) { this.exchange = exchange; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public void setTitle(String title) { this.title = title; }
    public void setYesPrice(BigDecimal yesPrice) { this.yesPrice = yesPrice; }
    public void setNoPrice(BigDecimal noPrice) { this.noPrice = noPrice; }
    public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }

    @Override
    public String toString() {
        return String.format("RawEvent{exchange='%s', title='%s', yes=%.3f, no=%.3f}",
                exchange, title, yesPrice, noPrice);
    }
}