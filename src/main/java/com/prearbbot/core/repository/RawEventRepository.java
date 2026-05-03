package com.prearbbot.core.repository;

import com.prearbbot.core.model.RawEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class RawEventRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void save(RawEvent event) {
        String sql = """
            INSERT INTO raw_events (exchange, external_id, title, yes_price, no_price, fetched_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        jdbcTemplate.update(sql,
                event.getExchange(),
                event.getExternalId(),
                event.getTitle(),
                event.getYesPrice(),
                event.getNoPrice(),
                Timestamp.valueOf(event.getFetchedAt())
        );
    }

    public void saveAll(List<RawEvent> events) {
        for (RawEvent event : events) {
            save(event);
        }
    }

    public List<RawEvent> findAll() {
        String sql = "SELECT * FROM raw_events ORDER BY id";
        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new RawEvent(
                        rs.getLong("id"),
                        rs.getString("exchange"),
                        rs.getString("external_id"),
                        rs.getString("title"),
                        rs.getBigDecimal("yes_price"),
                        rs.getBigDecimal("no_price"),
                        rs.getTimestamp("fetched_at").toLocalDateTime()
                )
        );
    }

    public Optional<RawEvent> findById(Long id) {
        String sql = "SELECT * FROM raw_events WHERE id = ?";
        try {
            RawEvent event = jdbcTemplate.queryForObject(sql, (rs, rowNum) ->
                    new RawEvent(
                            rs.getLong("id"),
                            rs.getString("exchange"),
                            rs.getString("external_id"),
                            rs.getString("title"),
                            rs.getBigDecimal("yes_price"),
                            rs.getBigDecimal("no_price"),
                            rs.getTimestamp("fetched_at").toLocalDateTime()
                    ), id);
            return Optional.ofNullable(event);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}