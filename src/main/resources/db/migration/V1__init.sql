-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Markets registry
CREATE TABLE IF NOT EXISTS markets (
                                       id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    platform_id VARCHAR(32) NOT NULL,
    external_id VARCHAR(128) NOT NULL,
    title TEXT NOT NULL,
    source_url VARCHAR(512) NOT NULL,
    category VARCHAR(64),
    expiration_date TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(platform_id, external_id)
    );
CREATE INDEX idx_markets_platform_exp ON markets(platform_id, expiration_date DESC);

-- Price history (time-series)
CREATE TABLE IF NOT EXISTS market_prices (
                                             id BIGSERIAL PRIMARY KEY,
                                             market_id UUID NOT NULL,  -- FK убран для jOOQ
                                             yes_price DECIMAL(10,6) NOT NULL,
    no_price DECIMAL(10,6) NOT NULL,
    recorded_at TIMESTAMPTZ DEFAULT NOW()
    );
CREATE INDEX idx_prices_market_time ON market_prices(market_id, recorded_at DESC);

-- Таблица платформ
CREATE TABLE IF NOT EXISTS platforms (
                                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

-- Таблица исходов (outcomes)
CREATE TABLE IF NOT EXISTS outcomes (
                                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    market_id UUID NOT NULL REFERENCES markets(id) ON DELETE CASCADE,
    label VARCHAR(255) NOT NULL,
    price DECIMAL(10,4) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(market_id, label)
    );

-- Индексы для производительности
CREATE INDEX IF NOT EXISTS idx_outcomes_market_id ON outcomes(market_id);
CREATE INDEX IF NOT EXISTS idx_markets_platform_id ON markets(platform_id);

-- AI-matched pairs (исключаем из генерации, но таблица нужна)
CREATE TABLE IF NOT EXISTS arbitrage_pairs (
                                               id BIGSERIAL PRIMARY KEY,
                                               market_a_id UUID NOT NULL,  -- FK убран
                                               market_b_id UUID NOT NULL,  -- FK убран
                                               confidence DECIMAL(5,4) NOT NULL,
    status VARCHAR(16) DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(market_a_id, market_b_id)
    );

-- Signals found by scanner (ОСНОВНАЯ таблица)
CREATE TABLE IF NOT EXISTS arbitrage_signals (
                                                 id BIGSERIAL PRIMARY KEY,
                                                 pair_id BIGINT,  -- FK убран
                                                 event_title TEXT,           -- <= ДОБАВЛЕНО
                                                 platform_yes VARCHAR(32),   -- <= ДОБАВЛЕНО
    platform_no VARCHAR(32),    -- <= ДОБАВЛЕНО
    price_yes DECIMAL(10,6),    -- <= ДОБАВЛЕНО
    price_no DECIMAL(10,6),     -- <= ДОБАВЛЕНО
    profit_percent DECIMAL(8,4), -- <= ДОБАВЛЕНО
    url_yes VARCHAR(512),       -- <= ДОБАВЛЕНО
    url_no VARCHAR(512),        -- <= ДОБАВЛЕНО
    spread_percent DECIMAL(6,4),
    expected_profit DECIMAL(8,4),
    status VARCHAR(16) DEFAULT 'NEW',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    disappeared_at TIMESTAMPTZ,
    confirmed_by_chat_id BIGINT,
    placed_at TIMESTAMPTZ
    );
CREATE INDEX idx_signals_status_time ON arbitrage_signals(status, created_at DESC);
CREATE INDEX idx_signals_pending ON arbitrage_signals(status, expires_at) WHERE status IN ('SENT', 'CONFIRMED');

-- Orders (real or dry-run)
CREATE TABLE IF NOT EXISTS orders (
                                      id BIGSERIAL PRIMARY KEY,
                                      signal_id BIGINT,  -- FK убран
                                      platform_id VARCHAR(32) NOT NULL,
    market_id UUID,  -- FK убран
    side VARCHAR(4) NOT NULL CHECK (side IN ('YES','NO')),
    amount DECIMAL(12,4) NOT NULL,
    price DECIMAL(10,6) NOT NULL,
    status VARCHAR(16) DEFAULT 'PENDING',
    external_ref VARCHAR(128),
    created_at TIMESTAMPTZ DEFAULT NOW()
    );

-- Current positions
CREATE TABLE IF NOT EXISTS positions (
                                         id BIGSERIAL PRIMARY KEY,
                                         market_id UUID NOT NULL,  -- FK убран
                                         side VARCHAR(4) NOT NULL,
    amount DECIMAL(12,4) NOT NULL,
    avg_price DECIMAL(10,6) NOT NULL,
    unrealized_pnl DECIMAL(10,4) DEFAULT 0,
    is_open BOOLEAN DEFAULT TRUE,
    UNIQUE(market_id, side)
    );

-- Telegram notifications log
CREATE TABLE IF NOT EXISTS telegram_notifications (
                                                      id BIGSERIAL PRIMARY KEY,
                                                      signal_id BIGINT,  -- FK убран
                                                      chat_id BIGINT NOT NULL,
                                                      message_id BIGINT,
                                                      sent_at TIMESTAMPTZ DEFAULT NOW(),
    responded_at TIMESTAMPTZ,
    response_text VARCHAR(32)
    );
CREATE INDEX idx_tg_notifications_signal ON telegram_notifications(signal_id);

-- Ограничение на статусы (опционально)
ALTER TABLE arbitrage_signals
    ADD CONSTRAINT chk_signal_status
        CHECK (status IN ('NEW','SENT','CONFIRMED','PLACED','EXPIRED','FAILED'));


-- В конце V1__init.sql (теперь V1__init.sql)
CREATE TABLE IF NOT EXISTS matched_markets (
                                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    polymarket_market_id UUID NOT NULL REFERENCES markets(id),
    kalshi_market_id UUID NOT NULL REFERENCES markets(id),
    confidence NUMERIC(3,2) NOT NULL,
    matched_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(polymarket_market_id, kalshi_market_id)
    );