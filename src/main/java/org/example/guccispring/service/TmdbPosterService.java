package org.example.guccispring.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class TmdbPosterService {

    private static final Logger log =
            LoggerFactory.getLogger(TmdbPosterService.class);

    private static final Pattern POSTER_PATH =
            Pattern.compile("^/[a-zA-Z0-9_-]+\\.(jpg|jpeg|png|webp)$");

    private static final String IMAGE_BASE =
            "https://image.tmdb.org/t/p/w500";

    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;

    private final JdbcTemplate jdbc;
    private final RestClient client;
    private final RestClient imageClient;
    private final String token;
    private final Path posterDirectory;

    public record PosterOption(
            long tmdbId,
            String title,
            String originalTitle,
            Integer year,
            String posterUrl,
            String type
    ) {}

    public record DownloadResult(String localPath) {}

    public record BatchResult(
            int processed,
            int saved,
            int skipped,
            long nextAfterId,
            boolean hasMore,
            List<String> savedTitles
    ) {}

    private record ExistingMedia(
            long id,
            String title,
            String originalTitle,
            int year,
            String type,
            String existingPosterUrl
    ) {}

    public TmdbPosterService(
            JdbcTemplate jdbc,
            @Value("${tmdb.api-token:}") String token,
            @Value("${posters.storage-dir:./src/main/resources/static/img/posters}") String storageDir
    ) {
        this.jdbc = jdbc;
        this.token = token.trim();

        this.posterDirectory = Path.of(storageDir)
                .toAbsolutePath()
                .normalize();

        SimpleClientHttpRequestFactory apiFactory =
                new SimpleClientHttpRequestFactory();

        apiFactory.setConnectTimeout(5000);
        apiFactory.setReadTimeout(10000);

        client = RestClient.builder()
                .baseUrl("https://api.themoviedb.org/3")
                .requestFactory(apiFactory)
                .build();

        SimpleClientHttpRequestFactory imageFactory =
                new SimpleClientHttpRequestFactory();

        imageFactory.setConnectTimeout(5000);
        imageFactory.setReadTimeout(15000);

        imageClient = RestClient.builder()
                .baseUrl(IMAGE_BASE)
                .requestFactory(imageFactory)
                .build();
    }

    private void checkToken() {
        if (token.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "TMDB_API_TOKEN не настроен"
            );
        }
    }

    private JsonNode searchTmdb(String title, String type) {
        checkToken();

        String category = switch (type) {
            case "MOVIE" -> "movie";
            case "SERIES" -> "tv";
            default -> throw new IllegalArgumentException(
                    "Тип должен быть MOVIE или SERIES"
            );
        };

        try {
            return client.get()
                    .uri(builder -> builder
                            .path("/search/" + category)
                            .queryParam("query", title)
                            .queryParam("language", "ru-RU")
                            .queryParam("include_adult", false)
                            .build())
                    .headers(headers ->
                            headers.setBearerAuth(token))
                    .retrieve()
                    .body(JsonNode.class);

        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();

            if (status == 401 || status == 403) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "TMDB отклонил токен",
                        e
                );
            }

            if (status == 429) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "TMDB временно ограничил количество запросов",
                        e
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Ошибка TMDB",
                    e
            );

        } catch (RestClientException e) {
            log.error("TMDB search error", e);

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Не удалось подключиться к TMDB",
                    e
            );
        }
    }

    private Integer year(String date) {
        if (date == null || date.length() < 4) {
            return null;
        }

        try {
            return Integer.parseInt(date.substring(0, 4));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String posterUrl(String path) {
        if (path == null || !POSTER_PATH.matcher(path).matches()) {
            return null;
        }

        return IMAGE_BASE + path;
    }

    private String normalized(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    public List<PosterOption> search(
            String title,
            String type,
            Integer expectedYear
    ) {
        JsonNode response = searchTmdb(title.trim(), type);

        if (response == null || !response.path("results").isArray()) {
            return List.of();
        }

        List<PosterOption> results = new ArrayList<>();

        for (JsonNode item : response.path("results")) {
            if (item.path("adult").asBoolean(false)) {
                continue;
            }

            String url = posterUrl(
                    item.path("poster_path").asText(null)
            );

            if (url == null) {
                continue;
            }

            String name = "MOVIE".equals(type)
                    ? item.path("title").asText("")
                    : item.path("name").asText("");

            String original = "MOVIE".equals(type)
                    ? item.path("original_title").asText("")
                    : item.path("original_name").asText("");

            String release = "MOVIE".equals(type)
                    ? item.path("release_date").asText("")
                    : item.path("first_air_date").asText("");

            if (name.isBlank()) {
                name = original;
            }

            long tmdbId = item.path("id").asLong(0);

            if (name.isBlank() || tmdbId <= 0) {
                continue;
            }

            results.add(new PosterOption(
                    tmdbId,
                    name,
                    original,
                    year(release),
                    url,
                    type
            ));
        }

        String wanted = normalized(title);

        results.sort(
                Comparator
                        .comparingInt((PosterOption p) ->
                                normalized(p.title()).equals(wanted)
                                        || normalized(p.originalTitle()).equals(wanted)
                                        ? 0 : 1
                        )
                        .thenComparingInt(p ->
                                expectedYear != null
                                        && expectedYear.equals(p.year())
                                        ? 0 : 1
                        )
        );

        return results.stream().limit(12).toList();
    }

    public DownloadResult downloadPoster(String posterUrl) {
        if (posterUrl == null || posterUrl.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ссылка на постер отсутствует"
            );
        }

        URI uri;

        try {
            uri = URI.create(posterUrl);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Некорректная ссылка TMDB"
            );
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"image.tmdb.org".equalsIgnoreCase(uri.getHost())
                || uri.getPort() != -1
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || uri.getPath() == null
                || !uri.getPath().startsWith("/t/p/")) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Разрешены только ссылки на изображения TMDB"
            );
        }

        String sourcePath = uri.getPath();

        String filename = sourcePath.substring(
                sourcePath.lastIndexOf('/')
        );

        if (!POSTER_PATH.matcher(filename).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Недопустимое имя файла постера"
            );
        }

        String savedName = "tmdb-" + filename.substring(1);

        Path destination = posterDirectory
                .resolve(savedName)
                .normalize();

        if (!destination.startsWith(posterDirectory)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Недопустимый путь к файлу"
            );
        }

        try {
            Files.createDirectories(posterDirectory);

            if (!Files.isRegularFile(destination)
                    || Files.size(destination) == 0) {

                byte[] content = imageClient.get()
                        .uri(filename)
                        .retrieve()
                        .body(byte[].class);

                if (content == null
                        || content.length == 0
                        || content.length > MAX_IMAGE_BYTES) {

                    throw new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY,
                            "TMDB вернул пустое или слишком большое изображение"
                    );
                }

                Path temp = Files.createTempFile(
                        posterDirectory,
                        "tmdb-",
                        ".part"
                );

                try {
                    Files.write(temp, content);

                    Files.move(
                            temp,
                            destination,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                } finally {
                    Files.deleteIfExists(temp);
                }
            }

            return new DownloadResult(
                    "/img/posters/" + savedName
            );

        } catch (IOException | RestClientException e) {
            log.error("TMDB poster download error: {}", posterUrl, e);

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Не удалось скачать изображение TMDB",
                    e
            );
        }
    }

    private Optional<PosterOption> findMatchingPoster(ExistingMedia media) {
        List<PosterOption> options = search(
                media.title(),
                media.type(),
                media.year()
        );

        String localName = normalized(media.title());
        String originalName = normalized(media.originalTitle());

        Optional<PosterOption> match = options.stream()
                .filter(option ->
                        option.year() != null
                                && option.year() == media.year()
                )
                .filter(option ->
                        normalized(option.title()).equals(localName)
                                || normalized(option.originalTitle()).equals(localName)
                                || (
                                !originalName.isBlank()
                                        && (
                                        normalized(option.originalTitle()).equals(originalName)
                                                || normalized(option.title()).equals(originalName)
                                )
                        )
                )
                .findFirst();

        if (match.isPresent()) {
            return match;
        }

        if (media.originalTitle() == null
                || media.originalTitle().isBlank()
                || normalized(media.originalTitle()).equals(localName)) {
            return Optional.empty();
        }

        return search(
                media.originalTitle(),
                media.type(),
                media.year()
        ).stream()
                .filter(option ->
                        option.year() != null
                                && option.year() == media.year()
                )
                .filter(option ->
                        normalized(option.originalTitle()).equals(originalName)
                                || normalized(option.title()).equals(originalName)
                )
                .findFirst();
    }

    public BatchResult autofill(long afterId, int limit) {
        checkToken();

        List<ExistingMedia> items = jdbc.query(
                """
                SELECT
                    m.id,
                    m.title,
                    m.original_title,
                    m.release_year,
                    m.media_type,
                    p.poster_url AS existing_poster_url
                FROM media m
                LEFT JOIN LATERAL (
                    SELECT poster_url, local_path
                    FROM media_posters
                    WHERE media_id = m.id
                      AND is_primary = TRUE
                    ORDER BY id DESC
                    LIMIT 1
                ) p ON TRUE
                WHERE m.id > ?
                  AND m.published = TRUE
                  AND (
                      p.poster_url IS NULL
                      AND p.local_path IS NULL
                      OR p.poster_url LIKE 'https://image.tmdb.org/t/p/%'
                      OR p.local_path LIKE '/api/posters/%'
                  )
                  AND (
                      p.local_path IS NULL
                      OR p.local_path NOT LIKE '/img/posters/%'
                  )
                ORDER BY m.id
                LIMIT ?
                """,
                (rs, row) -> new ExistingMedia(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("original_title"),
                        rs.getInt("release_year"),
                        rs.getString("media_type"),
                        rs.getString("existing_poster_url")
                ),
                afterId,
                limit
        );

        int saved = 0;
        int skipped = 0;
        long lastId = afterId;

        List<String> savedTitles = new ArrayList<>();

        for (ExistingMedia media : items) {
            lastId = media.id();

            try {
                String sourceUrl = media.existingPosterUrl();

                if (sourceUrl == null
                        || !sourceUrl.startsWith(
                        "https://image.tmdb.org/t/p/"
                )) {

                    Optional<PosterOption> match =
                            findMatchingPoster(media);

                    if (match.isEmpty()) {
                        skipped++;
                        log.info(
                                "TMDB poster not found for {} ({})",
                                media.title(),
                                media.year()
                        );
                        continue;
                    }

                    sourceUrl = match.get().posterUrl();
                }

                String localPath = downloadPoster(
                        sourceUrl
                ).localPath();

                int updated = jdbc.update(
                        """
                        UPDATE media_posters
                        SET
                            local_path = ?,
                            poster_url = NULL,
                            provider = 'TMDB'
                        WHERE media_id = ?
                          AND is_primary = TRUE
                          AND (
                              local_path IS NULL
                              OR local_path NOT LIKE '/img/posters/%'
                          )
                        """,
                        localPath,
                        media.id()
                );

                if (updated == 0) {
                    updated = jdbc.update(
                            """
                            INSERT INTO media_posters (
                                media_id,
                                provider,
                                local_path,
                                is_primary
                            )
                            SELECT ?, 'TMDB', ?, TRUE
                            WHERE NOT EXISTS (
                                SELECT 1
                                FROM media_posters
                                WHERE media_id = ?
                                  AND is_primary = TRUE
                            )
                            """,
                            media.id(),
                            localPath,
                            media.id()
                    );
                }

                if (updated > 0) {
                    saved++;
                    savedTitles.add(media.title());

                    log.info(
                            "Local TMDB poster saved: {} -> {}",
                            media.title(),
                            localPath
                    );
                } else {
                    skipped++;
                }

            } catch (Exception e) {
                skipped++;

                log.warn(
                        "Poster skipped for {} (ID {})",
                        media.title(),
                        media.id(),
                        e
                );
            }
        }

        return new BatchResult(
                items.size(),
                saved,
                skipped,
                lastId,
                items.size() == limit,
                savedTitles
        );
    }
}