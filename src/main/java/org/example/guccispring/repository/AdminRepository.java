
package org.example.guccispring.repository;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class AdminRepository {
    public record Spec(List<String> keys, Set<String> columns) {
        public static Spec of(String keys, String columns) {
            return new Spec(
                    List.of(keys.split(",")),
                    new LinkedHashSet<>(List.of(columns.split(",")))
            );
        }
    }

    public static final Map<String, Spec> TABLES = Map.ofEntries(
            Map.entry("media", Spec.of("id",
                    "id,title,original_title,media_type,release_year,external_rating,overview,age_rating,premiere_date,theme_key,theme_glow,published")),
            Map.entry("films", Spec.of("media_id",
                    "media_id,media_type,runtime_minutes,budget_usd")),
            Map.entry("series", Spec.of("media_id",
                    "media_id,media_type,seasons_count,episodes_count,episode_runtime_minutes,end_year")),
            Map.entry("genres", Spec.of("id", "id,name")),
            Map.entry("media_genres", Spec.of("media_id,genre_id", "media_id,genre_id")),
            Map.entry("tags", Spec.of("id", "id,name")),
            Map.entry("media_tags", Spec.of("media_id,tag_id", "media_id,tag_id")),
            Map.entry("people", Spec.of("id", "id,full_name,original_name,photo_url")),
            Map.entry("credit_roles", Spec.of("id", "id,code,name")),
            Map.entry("media_credits", Spec.of("id",
                    "id,media_id,person_id,role_id,character_name,billing_order")),
            Map.entry("countries", Spec.of("id", "id,name")),
            Map.entry("media_countries", Spec.of("media_id,country_id", "media_id,country_id")),
            Map.entry("studios", Spec.of("id", "id,name")),
            Map.entry("media_studios", Spec.of("media_id,studio_id", "media_id,studio_id")),
            Map.entry("featured_media", Spec.of("media_id",
                    "media_id,position,active")),
            Map.entry("media_posters", Spec.of("id",
                    "id,media_id,provider,poster_url,local_path,language_code,is_primary")),
            Map.entry("media_external_ids", Spec.of("media_id,provider",
                    "media_id,provider,external_id")),
            Map.entry("user_ratings", Spec.of("user_id,media_id",
                    "user_id,media_id,rating")),
            Map.entry("watchlist", Spec.of("user_id,media_id",
                    "user_id,media_id"))
    );

    private final NamedParameterJdbcTemplate jdbc;

    public AdminRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Spec spec(String table) {
        Spec spec = TABLES.get(table);
        if (spec == null) throw new IllegalArgumentException("Неизвестная таблица");
        return spec;
    }

    private Map<String, Object> convert(Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();

        body.forEach((key, value) -> {
            if (value instanceof String s && key.endsWith("_date")) {
                result.put(key, LocalDate.parse(s));
            } else {
                result.put(key, value);
            }
        });

        return result;
    }

    private Map<String, Object> keyParams(Spec spec, String key) {
        String[] parts = key.split(":", -1);

        if (parts.length != spec.keys().size()) {
            throw new IllegalArgumentException("Некорректный составной ключ");
        }

        Map<String, Object> params = new LinkedHashMap<>();

        for (int i = 0; i < parts.length; i++) {
            String name = spec.keys().get(i);
            Object value = name.equals("provider") ? parts[i] : Long.parseLong(parts[i]);
            params.put("key" + i, value);
        }

        return params;
    }

    private String where(Spec spec) {
        List<String> conditions = new ArrayList<>();

        for (int i = 0; i < spec.keys().size(); i++) {
            conditions.add(spec.keys().get(i) + " = :key" + i);
        }

        return String.join(" AND ", conditions);
    }

    public List<Map<String, Object>> all(String table, int limit, int offset) {
        Spec spec = spec(table);
        String order = String.join(", ", spec.keys());

        return jdbc.queryForList(
                "SELECT * FROM " + table +
                        " ORDER BY " + order +
                        " LIMIT :limit OFFSET :offset",
                Map.of("limit", limit, "offset", offset)
        );
    }

    public Map<String, Object> one(String table, String key) {
        Spec spec = spec(table);

        List<Map<String, Object>> result = jdbc.queryForList(
                "SELECT * FROM " + table + " WHERE " + where(spec),
                keyParams(spec, key)
        );

        if (result.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Запись не найдена");
        }

        return result.get(0);
    }

    public Map<String, Object> create(String table, Map<String, Object> values) {
        spec(table);
        Map<String, Object> data = convert(values);
        String names = String.join(", ", data.keySet());
        String args = data.keySet().stream()
                .map(k -> ":" + k)
                .collect(Collectors.joining(", "));

        return jdbc.queryForMap(
                "INSERT INTO " + table + " (" + names + ") VALUES (" + args + ") RETURNING *",
                data
        );
    }

    public Map<String, Object> update(
            String table, String key, Map<String, Object> values) {
        Spec spec = spec(table);
        Map<String, Object> data = convert(values);

        spec.keys().forEach(data::remove);

        if (data.isEmpty()) {
            throw new IllegalArgumentException("Нет полей для обновления");
        }

        String assignments = data.keySet().stream()
                .map(k -> k + " = :" + k)
                .collect(Collectors.joining(", "));

        data.putAll(keyParams(spec, key));

        List<Map<String, Object>> result = jdbc.queryForList(
                "UPDATE " + table + " SET " + assignments +
                        " WHERE " + where(spec) + " RETURNING *",
                data
        );

        if (result.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Запись не найдена");
        }

        return result.get(0);
    }

    public void delete(String table, String key) {
        Spec spec = spec(table);

        int count = jdbc.update(
                "DELETE FROM " + table + " WHERE " + where(spec),
                keyParams(spec, key)
        );

        if (count == 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Запись не найдена");
        }
    }
}
