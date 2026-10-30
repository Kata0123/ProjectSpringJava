
package org.example.catalog;

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

    private Map<String, Object> mapMedia(ResultSet rs) throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();

        long id = rs.getLong("id");
        String mediaType = rs.getString("media_type");

        result.put("id", id);
        result.put("title", rs.getString("title"));
        result.put("type", "MOVIE".equals(mediaType) ? "Фильм" : "Сериал");
        result.put("year", rs.getInt("release_year"));
        result.put("rating", rs.getBigDecimal("external_rating"));
        result.put("genres", strings(rs.getArray("genres")));
        result.put("tags", strings(rs.getArray("tags")));
        result.put("poster", rs.getString("poster"));
        result.put("overview", rs.getString("overview"));
        result.put("theme", rs.getString("theme_key"));
        result.put("glow", rs.getString("theme_glow"));
        result.put("age", rs.getString("age_rating"));
        result.put("countries", strings(rs.getArray("countries")));
        result.put("studios", strings(rs.getArray("studios")));
        result.put("featuredPosition", rs.getObject("featured_position"));

        var premiere = rs.getDate("premiere_date");
        result.put(
                "premiere",
                premiere == null ? null : premiere.toLocalDate().toString()
        );

        Integer runtime = rs.getObject("runtime_minutes", Integer.class);

        if (runtime == null) {
            runtime = rs.getObject("episode_runtime_minutes", Integer.class);
        }

        result.put(
                "duration",
                runtime == null
                        ? null
                        : runtime / 60 + " ч " + runtime % 60 + " мин"
        );

        Long budget = rs.getObject("budget_usd", Long.class);

        result.put(
                "budget",
                budget == null
                        ? null
                        : "$" + String.format("%,d", budget).replace(',', ' ')
        );

        return result;
    }

    public List<Map<String, Object>> findAll() {
        return jdbc.query(
                "SELECT * FROM catalog WHERE published = TRUE ORDER BY id",
                (rs, row) -> mapMedia(rs)
        );
    }

    public Optional<Map<String, Object>> findById(long id) {
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT * FROM catalog WHERE published = TRUE AND id = ?",
                (rs, row) -> mapMedia(rs),
                id
        );

        return rows.stream().findFirst();
    }

    public List<String> people(long mediaId, String role) {
        return jdbc.query(
                """
                SELECT p.full_name
                FROM media_credits mc
                JOIN people p ON p.id = mc.person_id
                JOIN credit_roles cr ON cr.id = mc.role_id
                WHERE mc.media_id = ? AND cr.code = ?
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
}
