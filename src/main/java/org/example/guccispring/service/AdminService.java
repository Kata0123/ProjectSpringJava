
package org.example.guccispring.service;

import org.example.guccispring.repository.AdminRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class AdminService {
    private final AdminRepository repository;

    @Value("${app.admin-key:}")
    private String adminKey;

    public AdminService(AdminRepository repository) {
        this.repository = repository;
    }

    public void authorize(String supplied) {
        if (adminKey == null || adminKey.isBlank() || supplied == null ||
                !MessageDigest.isEqual(
                        adminKey.getBytes(StandardCharsets.UTF_8),
                        supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет прав администратора");
        }
    }

    public void validate(String table, Map<String, Object> data, boolean create) {
        var spec = repository.spec(table);

        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Передайте данные");
        }

        for (var entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (!spec.columns().contains(key)) {
                throw new IllegalArgumentException("Недопустимое поле: " + key);
            }

            if (value instanceof String text && text.length() > 5000) {
                throw new IllegalArgumentException("Слишком длинное значение: " + key);
            }

            if (value instanceof Number number &&
                    (key.endsWith("_id") || key.equals("id")) &&
                    number.longValue() < 1) {
                throw new IllegalArgumentException("Идентификатор должен быть положительным");
            }
        }

        if (create) {
            Map<String, List<String>> required = Map.ofEntries(
                    Map.entry("media", List.of("title", "media_type", "release_year")),
                    Map.entry("genres", List.of("name")),
                    Map.entry("tags", List.of("name")),
                    Map.entry("countries", List.of("name")),
                    Map.entry("studios", List.of("name")),
                    Map.entry("people", List.of("full_name")),
                    Map.entry("credit_roles", List.of("code", "name")),
                    Map.entry("media_posters", List.of("media_id", "provider")),
                    Map.entry("media_external_ids", List.of("media_id", "provider", "external_id")),
                    Map.entry("media_credits", List.of("media_id", "person_id", "role_id")),
                    Map.entry("featured_media", List.of("media_id", "position")),
                    Map.entry("user_ratings", List.of("user_id", "media_id", "rating")),
                    Map.entry("watchlist", List.of("user_id", "media_id"))
            );

            for (String field : required.getOrDefault(table, List.of())) {
                Object value = data.get(field);
                if (value == null || value instanceof String s && s.isBlank()) {
                    throw new IllegalArgumentException("Обязательное поле: " + field);
                }
            }
        }

        for (String field : List.of("title", "name", "full_name", "provider", "external_id")) {
            if (data.containsKey(field) &&
                    (!(data.get(field) instanceof String s) || s.isBlank())) {
                throw new IllegalArgumentException("Некорректное поле: " + field);
            }
        }

        if (data.containsKey("media_type") &&
                !Set.of("MOVIE", "SERIES").contains(data.get("media_type"))) {
            throw new IllegalArgumentException("Тип должен быть MOVIE или SERIES");
        }

        if (data.containsKey("release_year")) {
            int year = integer(data.get("release_year"));
            if (year < 1888 || year > 2100) {
                throw new IllegalArgumentException("Некорректный год");
            }
        }

        if (data.containsKey("rating")) {
            int rating = integer(data.get("rating"));
            if (rating < 1 || rating > 10) {
                throw new IllegalArgumentException("Оценка должна быть от 1 до 10");
            }
        }

        if (data.containsKey("external_rating") && data.get("external_rating") != null) {
            double rating = Double.parseDouble(data.get("external_rating").toString());
            if (rating < 0 || rating > 10) {
                throw new IllegalArgumentException("Рейтинг должен быть от 0 до 10");
            }
        }

        if (data.containsKey("poster_url") && data.get("poster_url") != null) {
            String url = data.get("poster_url").toString();
            if (!url.startsWith("https://") && !url.startsWith("http://")) {
                throw new IllegalArgumentException("Постер должен иметь HTTP(S)-адрес");
            }
        }
    }

    private int integer(Object value) {
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            throw new IllegalArgumentException("Ожидается целое число");
        }
    }

    public List<Map<String, Object>> all(String table, int limit, int offset) {
        if (limit < 1 || limit > 200 || offset < 0) {
            throw new IllegalArgumentException("Некорректные параметры страницы");
        }
        return repository.all(table, limit, offset);
    }

    public Map<String, Object> one(String table, String key) {
        return repository.one(table, key);
    }

    public Map<String, Object> create(String table, Map<String, Object> data) {
        validate(table, data, true);
        return repository.create(table, data);
    }

    public Map<String, Object> update(String table, String key, Map<String, Object> data) {
        validate(table, data, false);
        return repository.update(table, key, data);
    }

    public void delete(String table, String key) {
        repository.delete(table, key);
    }
}
