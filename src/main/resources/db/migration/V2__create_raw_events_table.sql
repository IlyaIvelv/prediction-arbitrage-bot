CREATE TABLE IF NOT EXISTS raw_events (
                                          id SERIAL PRIMARY KEY,
                                          exchange VARCHAR(50) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    title TEXT NOT NULL,
    yes_price DECIMAL(10,3) NOT NULL,
    no_price DECIMAL(10,3) NOT NULL,
    fetched_at TIMESTAMP NOT NULL
    );

CREATE INDEX idx_raw_events_exchange ON raw_events(exchange);
CREATE INDEX idx_raw_events_fetched_at ON raw_events(fetched_at);