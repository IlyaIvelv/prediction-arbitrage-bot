CREATE TABLE IF NOT EXISTS arbitrage_opportunities (
                                                       id SERIAL PRIMARY KEY,
                                                       event_match_id INTEGER NOT NULL REFERENCES event_matches(id),
    buy_exchange VARCHAR(50) NOT NULL,          -- где покупаем (gemini или polymarket)
    buy_outcome VARCHAR(10) NOT NULL,           -- YES или NO
    buy_price DECIMAL(10,4) NOT NULL,
    sell_exchange VARCHAR(50) NOT NULL,
    sell_outcome VARCHAR(10) NOT NULL,
    sell_price DECIMAL(10,4) NOT NULL,
    profit_percent DECIMAL(10,4) NOT NULL,      -- чистая прибыль
    created_at TIMESTAMP DEFAULT NOW()
    );