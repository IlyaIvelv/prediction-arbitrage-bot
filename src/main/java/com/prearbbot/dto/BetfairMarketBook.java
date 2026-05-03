package com.prearbbot.dto;

import java.math.BigDecimal;
import java.util.List;

public class BetfairMarketBook {
    private String marketId;
    private List<Runner> runners;

    public static class Runner {
        private Long selectionId;
        private String runnerName;
        private ExchangePrices exchangePrices;

        public static class ExchangePrices {
            private List<PriceSize> availableToBack;
            private List<PriceSize> availableToLay;

            public static class PriceSize {
                private BigDecimal price;
                private BigDecimal size;

                // геттеры и сеттеры
                public BigDecimal getPrice() { return price; }
                public void setPrice(BigDecimal price) { this.price = price; }
                public BigDecimal getSize() { return size; }
                public void setSize(BigDecimal size) { this.size = size; }
            }

            public List<PriceSize> getAvailableToBack() { return availableToBack; }
            public void setAvailableToBack(List<PriceSize> availableToBack) { this.availableToBack = availableToBack; }
            public List<PriceSize> getAvailableToLay() { return availableToLay; }
            public void setAvailableToLay(List<PriceSize> availableToLay) { this.availableToLay = availableToLay; }
        }

        public Long getSelectionId() { return selectionId; }
        public void setSelectionId(Long selectionId) { this.selectionId = selectionId; }
        public String getRunnerName() { return runnerName; }
        public void setRunnerName(String runnerName) { this.runnerName = runnerName; }
        public ExchangePrices getExchangePrices() { return exchangePrices; }
        public void setExchangePrices(ExchangePrices exchangePrices) { this.exchangePrices = exchangePrices; }
    }

    public String getMarketId() { return marketId; }
    public void setMarketId(String marketId) { this.marketId = marketId; }
    public List<Runner> getRunners() { return runners; }
    public void setRunners(List<Runner> runners) { this.runners = runners; }
}