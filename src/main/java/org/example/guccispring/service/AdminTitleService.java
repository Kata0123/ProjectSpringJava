
package org.example.guccispring.service;

import org.example.guccispring.dto.TitleRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class AdminTitleService {

    private final JdbcTemplate jdbc;
    private final CatalogService catalog;

    public AdminTitleService(JdbcTemplate jdbc, CatalogService catalog) {
        this.jdbc = jdbc;
        this.catalog = catalog;
    }

    private List<String> names(List<String> values) {
        if (values == null) return List.of();

        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    private void validate(TitleRequest body) {
        if (body.endYear() != null
                && body.endYear() < body.releaseYear()) {
            throw new IllegalArgumentException(
                    "Год окончания не может быть раньше года выхода"
            );
        }

        String poster = body.posterUrl();

        if (poster != null && !poster.isBlank()
                && !poster.startsWith("https://")
                && !poster.startsWith("http://")
                && !poster.startsWith("/img/")) {
            throw new IllegalArgumentException(
                    "Укажите HTTP(S)-ссылку на постер или путь /img/"
            );
        }

        if (body.themeKey() != null && !body.themeKey().isBlank()
                && !Set.of(
                "upside", "web", "sand", "ember",
                "space", "rain", "code", "snow", "gold"
        ).contains(body.themeKey())) {
            throw new IllegalArgumentException("Неизвестная тема оформления");
        }
    }

    private long createMedia(TitleRequest body) {
        Long id = jdbc.queryForObject(
                """
                INSERT INTO media(
                    title, original_title, media_type,
                    release_year, external_rating, overview,
                    age_rating, premiere_date,
                    theme_key, theme_glow, published
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)
                RETURNING id
                """,
                Long.class,
                body.title().trim(),
                body.originalTitle(),
                body.mediaType(),
                body.releaseYear(),
                body.externalRating(),
                body.overview(),
                body.ageRating(),
                body.premiereDate(),
                body.themeKey(),
                body.themeGlow()
        );

        return Objects.requireNonNull(id);
    }

    private void updateMedia(long id, TitleRequest body) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM media WHERE id = ?",
                Integer.class,
                id
        );

        if (exists == null || exists == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Фильм или сериал не найден"
            );
        }

        jdbc.update("DELETE FROM films WHERE media_id = ?", id);
        jdbc.update("DELETE FROM series WHERE media_id = ?", id);

        jdbc.update(
                """
                UPDATE media
                SET title = ?,
                    original_title = ?,
                    media_type = ?,
                    release_year = ?,
                    external_rating = ?,
                    overview = ?,
                    age_rating = ?,
                    premiere_date = ?,
                    theme_key = ?,
                    theme_glow = ?
                WHERE id = ?
                """,
                body.title().trim(),
                body.originalTitle(),
                body.mediaType(),
                body.releaseYear(),
                body.externalRating(),
                body.overview(),
                body.ageRating(),
                body.premiereDate(),
                body.themeKey(),
                body.themeGlow(),
                id
        );
    }

    private void subtype(long id, TitleRequest body) {
        if ("MOVIE".equals(body.mediaType())) {
            jdbc.update(
                    """
                    INSERT INTO films(
                        media_id, runtime_minutes, budget_usd
                    )
                    VALUES (?, ?, ?)
                    """,
                    id,
                    body.runtimeMinutes(),
                    body.budgetUsd()
            );
        } else {
            jdbc.update(
                    """
                    INSERT INTO series(
                        media_id, seasons_count,
                        episodes_count, episode_runtime_minutes,
                        end_year
                    )
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    id,
                    body.seasonsCount(),
                    body.episodesCount(),
                    body.episodeRuntimeMinutes(),
                    body.endYear()
            );
        }
    }

    private void referenceLinks(
            long mediaId,
            String referenceTable,
            String junctionTable,
            String idColumn,
            List<String> values
    ) {
        jdbc.update(
                "DELETE FROM " + junctionTable + " WHERE media_id = ?",
                mediaId
        );

        for (String name : names(values)) {
            jdbc.update(
                    "INSERT INTO " + referenceTable +
                            "(name) VALUES (?) ON CONFLICT (name) DO NOTHING",
                    name
            );

            Long id = jdbc.queryForObject(
                    "SELECT id FROM " + referenceTable + " WHERE name = ?",
                    Long.class,
                    name
            );

            jdbc.update(
                    "INSERT INTO " + junctionTable +
                            "(media_id, " + idColumn + ") VALUES (?, ?)",
                    mediaId,
                    id
            );
        }
    }

    private void credits(
            long mediaId,
            String code,
            List<String> persons
    ) {
        Short roleId = jdbc.queryForObject(
                "SELECT id FROM credit_roles WHERE code = ?",
                Short.class,
                code
        );

        if (roleId == null) {
            throw new IllegalArgumentException(
                    "Не найдена роль участника: " + code
            );
        }

        int position = 1;

        for (String name : names(persons)) {
            jdbc.update(
                    """
                    INSERT INTO people(full_name)
                    VALUES (?)
                    ON CONFLICT DO NOTHING
                    """,
                    name
            );

            Long personId = jdbc.queryForObject(
                    """
                    SELECT id
                    FROM people
                    WHERE LOWER(full_name) = LOWER(?)
                    """,
                    Long.class,
                    name
            );

            jdbc.update(
                    """
                    INSERT INTO media_credits(
                        media_id, person_id, role_id, billing_order
                    )
                    VALUES (?, ?, ?, ?)
                    """,
                    mediaId,
                    personId,
                    roleId,
                    position++
            );
        }
    }

    private void additionalData(long id, TitleRequest body) {
        referenceLinks(
                id, "genres", "media_genres", "genre_id", body.genres()
        );

        referenceLinks(
                id, "tags", "media_tags", "tag_id", body.tags()
        );

        referenceLinks(
                id, "countries", "media_countries",
                "country_id", body.countries()
        );

        referenceLinks(
                id, "studios", "media_studios",
                "studio_id", body.studios()
        );

        jdbc.update(
                "DELETE FROM media_credits WHERE media_id = ?",
                id
        );

        credits(id, "DIRECTOR", body.directors());
        credits(id, "ACTOR", body.actors());

        jdbc.update(
                "DELETE FROM media_posters WHERE media_id = ? AND is_primary = TRUE",
                id
        );

        if (body.posterUrl() != null && !body.posterUrl().isBlank()) {
            jdbc.update(
                    """
                    INSERT INTO media_posters(
                        media_id, provider, poster_url, is_primary
                    )
                    VALUES (?, 'MANUAL', ?, TRUE)
                    """,
                    id,
                    body.posterUrl().trim()
            );
        }

        jdbc.update(
                "DELETE FROM featured_media WHERE media_id = ?",
                id
        );

        if (Boolean.TRUE.equals(body.featured())) {
            Integer position = jdbc.queryForObject(
                    "SELECT COALESCE(MAX(position), 0) + 1 FROM featured_media",
                    Integer.class
            );

            jdbc.update(
                    """
                    INSERT INTO featured_media(media_id, position, active)
                    VALUES (?, ?, TRUE)
                    """,
                    id,
                    position
            );
        }
    }

    @Transactional
    public long create(TitleRequest body) {
        validate(body);

        long id = createMedia(body);
        subtype(id, body);
        additionalData(id, body);

        return id;
    }

    @Transactional
    public void update(long id, TitleRequest body) {
        validate(body);

        updateMedia(id, body);
        subtype(id, body);
        additionalData(id, body);
    }

    @Transactional
    public void delete(long id) {
        int rows = jdbc.update(
                "DELETE FROM media WHERE id = ?",
                id
        );

        if (rows == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Фильм или сериал не найден"
            );
        }
    }

    public Map<String, Object> get(long id) {
        Map<String, Object> details = new LinkedHashMap<>(
                catalog.movie(id)
        );

        Map<String, Object> row;

        try {
            row = jdbc.queryForMap(
                    """
                    SELECT
                        m.original_title,
                        m.media_type,
                        m.release_year,
                        m.external_rating,
                        m.overview,
                        m.age_rating,
                        m.premiere_date,
                        m.theme_key,
                        m.theme_glow,
                        f.runtime_minutes,
                        f.budget_usd,
                        s.seasons_count,
                        s.episodes_count,
                        s.episode_runtime_minutes,
                        s.end_year,
                        EXISTS(
                            SELECT 1 FROM featured_media fm
                            WHERE fm.media_id = m.id AND fm.active = TRUE
                        ) AS featured
                    FROM media m
                    LEFT JOIN films f ON f.media_id = m.id
                    LEFT JOIN series s ON s.media_id = m.id
                    WHERE m.id = ?
                    """,
                    id
            );
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Запись не найдена"
            );
        }

        details.put("originalTitle", row.get("original_title"));
        details.put("mediaType", row.get("media_type"));
        details.put("releaseYear", row.get("release_year"));
        details.put("externalRating", row.get("external_rating"));
        details.put("ageRating", row.get("age_rating"));
        details.put("premiereDate", row.get("premiere_date"));
        details.put("themeKey", row.get("theme_key"));
        details.put("themeGlow", row.get("theme_glow"));
        details.put("runtimeMinutes", row.get("runtime_minutes"));
        details.put("budgetUsd", row.get("budget_usd"));
        details.put("seasonsCount", row.get("seasons_count"));
        details.put("episodesCount", row.get("episodes_count"));
        details.put("episodeRuntimeMinutes", row.get("episode_runtime_minutes"));
        details.put("endYear", row.get("end_year"));
        details.put("actors", details.get("cast"));
        details.put("featured", row.get("featured"));
        details.put("posterUrl", details.get("poster"));

        return details;
    }
}
