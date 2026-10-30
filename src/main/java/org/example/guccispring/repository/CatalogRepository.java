
package org.example.guccispring.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
public class CatalogRepository {

    private final JdbcTemplate jdbc;

    public CatalogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private List<String> strings(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }

        Object[] values = (Object[]) array.getArray();

        return Arrays.stream(values)
                .map(String::valueOf)
                .toList();
    }

    private Map<String, Object> movie(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();

        m.put("id", rs.getLong("id"));
        m.put("title", rs.getString("title"));

        String type = rs.getString("media_type");
        m.put("type", "MOVIE".equals(type) ? "Фильм" : "Сериал");

        m.put("year", rs.getInt("release_year"));
        m.put("rating", rs.getBigDecimal("external_rating"));
        m.put("genres", strings(rs.getArray("genres")));
        m.put("tags", strings(rs.getArray("tags")));
        m.put("poster", rs.getString("poster"));
        m.put("overview", rs.getString("overview"));
        m.put("theme", rs.getString("theme_key"));
        m.put("glow", rs.getString("theme_glow"));
        m.put("age", rs.getString("age_rating"));

        m.put("countries", strings(rs.getArray("countries")));
        m.put("studios", strings(rs.getArray("studios")));

        java.sql.Date premiere = rs.getDate("premiere_date");

        m.put(
                "premiere",
                premiere == null ? null : premiere.toLocalDate().toString()
        );

        Integer minutes = (Integer) rs.getObject("runtime_minutes");

        if (minutes == null) {
            minutes = (Integer) rs.getObject("episode_runtime_minutes");
        }

        m.put(
                "duration",
                minutes == null
                        ? null
                        : minutes / 60 + " ч " + minutes % 60 + " мин"
        );

        Long budget = (Long) rs.getObject("budget_usd");

        m.put(
                "budget",
                budget == null
                        ? null
                        : "$" + String.format("%,d", budget).replace(',', ' ')
        );

        m.put("featuredPosition", rs.getObject("featured_position"));

        return m;
    }

    public List<Map<String, Object>> findAll() {
        return jdbc.query(
                "SELECT * FROM catalog WHERE published = TRUE ORDER BY id",
                (rs, index) -> movie(rs)
        );
    }

    public Optional<Map<String, Object>> findById(long id) {
        List<Map<String, Object>> result = jdbc.query(
                "SELECT * FROM catalog WHERE id = ? AND published = TRUE",
                (rs, index) -> movie(rs),
                id
        );

        return result.stream().findFirst();
    }

    public List<String> people(long mediaId, String role) {
        return jdbc.query(
                """
                SELECT p.full_name
                FROM media_credits mc
                JOIN people p ON p.id = mc.person_id
                JOIN credit_roles cr ON cr.id = mc.role_id
                WHERE mc.media_id = ?
                  AND cr.code = ?
                ORDER BY mc.billing_order NULLS LAST, p.full_name
                """,
                (rs, row) -> rs.getString("full_name"),
                mediaId,
                role
        );
    }

    public List<Map<String, Object>> reference(String table) {
        Set<String> allowed = Set.of(
                "genres",
                "tags",
                "people",
                "countries",
                "studios",
                "credit_roles"
        );

        if (!allowed.contains(table)) {
            throw new IllegalArgumentException("Неизвестный справочник");
        }

        return jdbc.queryForList(
                "SELECT * FROM " + table + " ORDER BY id"
        );
    }

    public boolean exists(long id) {
        Boolean result = jdbc.queryForObject(
                """
                SELECT EXISTS(
                    SELECT 1
                    FROM media
                    WHERE id = ?
                    AND published = TRUE
                )
                """,
                Boolean.class,
                id
        );

        return Boolean.TRUE.equals(result);
    }
}
