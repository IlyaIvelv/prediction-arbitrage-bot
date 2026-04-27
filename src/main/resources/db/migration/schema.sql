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
                                             market_id UUID REFERENCES markets(id) ON DELETE CASCADE,
    yes_price DECIMAL(10,6) NOT NULL,
    no_price DECIMAL(10,6) NOT NULL,
    recorded_at TIMESTAMPTZ DEFAULT NOW()
    );
CREATE INDEX idx_prices_market_time ON market_prices(market_id, recorded_at DESC);

-- AI-matched pairs
CREATE TABLE IF NOT EXISTS arbitrage_pairs (
                                               id BIGSERIAL PRIMARY KEY,
                                               market_a_id UUID REFERENCES markets(id) ON DELETE CASCADE,
    market_b_id UUID REFERENCES markets(id) ON DELETE CASCADE,
    confidence DECIMAL(5,4) NOT NULL,
    status VARCHAR(16) DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(market_a_id, market_b_id)
    );

-- Signals found by scanner (основная таблица по ТЗ)
CREATE TABLE IF NOT EXISTS arbitrage_signals (
                                                 id BIGSERIAL PRIMARY KEY,
                                                 pair_id BIGINT REFERENCES arbitrage_pairs(id) ON DELETE CASCADE,
    spread_percent DECIMAL(6,4) NOT NULL,
    expected_profit DECIMAL(8,4) NOT NULL,
    status VARCHAR(16) DEFAULT 'NEW',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    expires_at TIMESTAMPTZ,          -- когда ждём подтверждения
    disappeared_at TIMESTAMPTZ,      -- когда вилка пропала
    confirmed_by_chat_id BIGINT,     -- кто нажал YES
    placed_at TIMESTAMPTZ            -- когда ставки размещены
    );
CREATE INDEX idx_signals_status_time ON arbitrage_signals(status, created_at DESC);
CREATE INDEX idx_signals_pending ON arbitrage_signals(status, expires_at) WHERE status IN ('SENT', 'CONFIRMED');

-- Orders (real or dry-run)
CREATE TABLE IF NOT EXISTS orders (
                                      id BIGSERIAL PRIMARY KEY,
                                      signal_id BIGINT REFERENCES arbitrage_signals(id) ON DELETE SET NULL,
    platform_id VARCHAR(32) NOT NULL,
    market_id UUID REFERENCES markets(id) ON DELETE SET NULL,
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
                                         market_id UUID REFERENCES markets(id) ON DELETE CASCADE,
    side VARCHAR(4) NOT NULL,
    amount DECIMAL(12,4) NOT NULL,
    avg_price DECIMAL(10,6) NOT NULL,
    unrealized_pnl DECIMAL(10,4) DEFAULT 0,
    is_open BOOLEAN DEFAULT TRUE,
    UNIQUE(market_id, side)
    );

-- Telegram notifications log (аудит ответов)
CREATE TABLE IF NOT EXISTS telegram_notifications (
                                                      id BIGSERIAL PRIMARY KEY,
                                                      signal_id BIGINT REFERENCES arbitrage_signals(id) ON DELETE CASCADE,
    chat_id BIGINT NOT NULL,
    message_id BIGINT,
    sent_at TIMESTAMPTZ DEFAULT NOW(),
    responded_at TIMESTAMPTZ,
    response_text VARCHAR(32)
    );
CREATE INDEX idx_tg_notifications_signal ON telegram_notifications(signal_id);

-- Ограничение на статусы сигналов (опционально, но рекомендуется)
ALTER TABLE arbitrage_signals
    ADD CONSTRAINT chk_signal_status
        CHECK (status IN ('NEW','SENT','CONFIRMED','PLACED','EXPIRED','FAILED'));