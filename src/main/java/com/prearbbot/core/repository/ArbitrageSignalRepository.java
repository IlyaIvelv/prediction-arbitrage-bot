package com.prearbbot.core.repository;

import com.prearbbot.core.model.ArbitrageSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.jooq.impl.DSL.*;

@Slf4j
@Repository
@RequiredArgsConstructor
@Transactional
public class ArbitrageSignalRepository {

    private final DSLContext dsl;
    private static final String TABLE = "arbitrage_signals";

    // === Типобезопасные поля ===
    public static final Field<Long> ID = field(name("id"), Long.class);
    public static final Field<Long> PAIR_ID = field(name("pair_id"), Long.class);
    public static final Field<String> EVENT_TITLE = field(name("event_title"), String.class);
    public static final Field<String> PLATFORM_YES = field(name("platform_yes"), String.class);
    public static final Field<String> PLATFORM_NO = field(name("platform_no"), String.class);
    public static final Field<BigDecimal> PRICE_YES = field(name("price_yes"), BigDecimal.class);
    public static final Field<BigDecimal> PRICE_NO = field(name("price_no"), BigDecimal.class);
    public static final Field<BigDecimal> PROFIT_PERCENT = field(name("profit_percent"), BigDecimal.class);
    public static final Field<String> URL_YES = field(name("url_yes"), String.class);
    public static final Field<String> URL_NO = field(name("url_no"), String.class);
    public static final Field<BigDecimal> SPREAD_PERCENT = field(name("spread_percent"), BigDecimal.class);
    public static final Field<BigDecimal> EXPECTED_PROFIT = field(name("expected_profit"), BigDecimal.class);
    public static final Field<String> STATUS = field(name("status"), String.class);
    public static final Field<OffsetDateTime> CREATED_AT = field(name("created_at"), OffsetDateTime.class);
    public static final Field<OffsetDateTime> EXPIRES_AT = field(name("expires_at"), OffsetDateTime.class);
    public static final Field<OffsetDateTime> DISAPPEARED_AT = field(name("disappeared_at"), OffsetDateTime.class);
    public static final Field<Long> CONFIRMED_BY_CHAT_ID = field(name("confirmed_by_chat_id"), Long.class);
    public static final Field<OffsetDateTime> PLACED_AT = field(name("placed_at"), OffsetDateTime.class);

    public ArbitrageSignal save(ArbitrageSignal signal) {
        var query = dsl.insertInto(table(TABLE))
                .set(PAIR_ID, signal.getPairId())
                .set(EVENT_TITLE, signal.getEventTitle())
                .set(PLATFORM_YES, signal.getPlatformYes())
                .set(PLATFORM_NO, signal.getPlatformNo())
                .set(PRICE_YES, signal.getPriceYes())
                .set(PRICE_NO, signal.getPriceNo())
                .set(PROFIT_PERCENT, signal.getProfitPercent())
                .set(URL_YES, signal.getUrlYes())
                .set(URL_NO, signal.getUrlNo())
                .set(SPREAD_PERCENT, signal.getSpreadPercent())
                .set(EXPECTED_PROFIT, signal.getExpectedProfit())
                .set(STATUS, signal.getStatus())
                .set(CREATED_AT, signal.getCreatedAt());

        if (signal.getExpiresAt() != null) query.set(EXPIRES_AT, signal.getExpiresAt());
        if (signal.getDisappearedAt() != null) query.set(DISAPPEARED_AT, signal.getDisappearedAt());
        if (signal.getConfirmedByChatId() != null) query.set(CONFIRMED_BY_CHAT_ID, signal.getConfirmedByChatId());
        if (signal.getPlacedAt() != null) query.set(PLACED_AT, signal.getPlacedAt());

        Record record = query.returning(ID).fetchOne();
        if (record != null) signal.setId(record.get(ID));

        log.debug("Saved signal: id={}, title={}, profit={}%",
                signal.getId(), signal.getEventTitle(), signal.getProfitPercent());
        return signal;
    }

    public Optional<ArbitrageSignal> findById(Long id) {
        return dsl.select(ID, PAIR_ID, EVENT_TITLE, PLATFORM_YES, PLATFORM_NO, PRICE_YES, PRICE_NO,
                        PROFIT_PERCENT, URL_YES, URL_NO, SPREAD_PERCENT, EXPECTED_PROFIT, STATUS,
                        CREATED_AT, EXPIRES_AT, DISAPPEARED_AT, CONFIRMED_BY_CHAT_ID, PLACED_AT)
                .from(table(TABLE))
                .where(ID.eq(id))
                .fetchOptional()
                .map(this::mapRecord);
    }

    public void markAsExpired(Long id) {
        dsl.update(table(TABLE))
                .set(STATUS, "EXPIRED")
                .set(DISAPPEARED_AT, OffsetDateTime.now())
                .where(ID.eq(id))
                .execute();
        log.debug("Signal {} marked as EXPIRED", id);
    }

    public void updateStatus(Long id, String status) {
        dsl.update(table(TABLE))
                .set(STATUS, status)
                .where(ID.eq(id))
                .execute();
    }

    public void updateExpiresAt(Long id, OffsetDateTime expiresAt) {
        dsl.update(table(TABLE))
                .set(STATUS, "SENT")
                .set(EXPIRES_AT, expiresAt)
                .where(ID.eq(id))
                .execute();
    }

    private ArbitrageSignal mapRecord(Record r) {
        return ArbitrageSignal.builder()
                .id(r.get(ID))
                .pairId(r.get(PAIR_ID))
                .eventTitle(r.get(EVENT_TITLE))
                .platformYes(r.get(PLATFORM_YES))
                .platformNo(r.get(PLATFORM_NO))
                .priceYes(r.get(PRICE_YES))
                .priceNo(r.get(PRICE_NO))
                .profitPercent(r.get(PROFIT_PERCENT))
                .urlYes(r.get(URL_YES))
                .urlNo(r.get(URL_NO))
                .spreadPercent(r.get(SPREAD_PERCENT))
                .expectedProfit(r.get(EXPECTED_PROFIT))
                .status(r.get(STATUS))
                .createdAt(r.get(CREATED_AT))
                .expiresAt(r.get(EXPIRES_AT))
                .disappearedAt(r.get(DISAPPEARED_AT))
                .confirmedByChatId(r.get(CONFIRMED_BY_CHAT_ID))
                .placedAt(r.get(PLACED_AT))
                .build();
    }
}