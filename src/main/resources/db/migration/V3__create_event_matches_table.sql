-- Таблица для хранения найденных схожих событий между биржами
CREATE TABLE IF NOT EXISTS event_matches (
                                             id SERIAL PRIMARY KEY,
                                             event_id_1 INTEGER NOT NULL,                    -- ID события из raw_events (например, Gemini)
                                             event_id_2 INTEGER NOT NULL,                    -- ID события из raw_events (Polymarket)
                                             similarity_score DECIMAL(5,4) NOT NULL,         -- Насколько похожи (0.85 = 85%)
    created_at TIMESTAMP DEFAULT NOW(),             -- Когда нашли связку
    UNIQUE(event_id_1, event_id_2)                  -- Защита от дубликатов
    );

-- Индекс для быстрого поиска
CREATE INDEX idx_event_matches_ids ON event_matches(event_id_1, event_id_2);