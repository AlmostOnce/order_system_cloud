package com.hbue.ordering.comment.repository;

import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RatingBatchRepository {
    public record Row(Long id, long count, Double average) {}
    private final NamedParameterJdbcTemplate jdbc;
    public RatingBatchRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<Row> windows(List<Long> ids) {
        return jdbc.query("SELECT window_id AS id, COUNT(*) AS cnt, AVG(score) AS avg_score FROM window_reviews WHERE window_id IN (:ids) GROUP BY window_id",
                new MapSqlParameterSource("ids", ids), (r, n) -> new Row(r.getLong("id"), r.getLong("cnt"), r.getDouble("avg_score")));
    }
    public List<Row> products(List<Long> ids) {
        return jdbc.query("SELECT product_id AS id, review_count AS cnt, score_total / NULLIF(review_count, 0) AS avg_score FROM comment_rating_stats WHERE product_id IN (:ids)",
                new MapSqlParameterSource("ids", ids), (r, n) -> new Row(r.getLong("id"), r.getLong("cnt"),
                        r.getObject("avg_score") == null ? null : r.getDouble("avg_score")));
    }
}
