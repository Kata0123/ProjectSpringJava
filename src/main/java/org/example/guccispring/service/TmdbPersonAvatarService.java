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
public class TmdbPersonAvatarService {

    private static final Logger log = LoggerFactory.getLogger(TmdbPersonAvatarService.class);
    private static final Pattern IMAGE_PATH = Pattern.compile("^/[a-zA-Z0-9_-]+\\.(jpg|jpeg|png|webp)$");
    private static final String IMAGE_BASE = "https://image.tmdb.org/t/p/w500";
    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;

    private final JdbcTemplate jdbc;
    private final RestClient client;
    private final RestClient imageClient;
    private final String token;
    private final Path peopleDirectory;

    public record PersonOption(long tmdbId, String name, String originalName, String photoUrl) {}
    public record DownloadResult(String localPath) {}
    public record BatchResult(int processed, int saved, int skipped, long nextAfterId, boolean hasMore, List<String> savedNames) {}
    private record ExistingPerson(long id, String fullName, String originalName) {}

    public TmdbPersonAvatarService(
            JdbcTemplate jdbc,
            @Value("${tmdb.api-token:}") String token,
            @Value("${people.storage-dir:./src/main/resources/static/img/people}") String storageDir
    ) {
        this.jdbc = jdbc;
        this.token = token.trim();
        this.peopleDirectory = Path.of(storageDir).toAbsolutePath().normalize();

        SimpleClientHttpRequestFactory apiFactory = new SimpleClientHttpRequestFactory();
        apiFactory.setConnectTimeout(4000);
        apiFactory.setReadTimeout(7000);
        this.client = RestClient.builder()
                .baseUrl("https://api.themoviedb.org/3")
                .requestFactory(apiFactory)
                .build();

        SimpleClientHttpRequestFactory imageFactory = new SimpleClientHttpRequestFactory();
        imageFactory.setConnectTimeout(5000);
        imageFactory.setReadTimeout(15000);
        this.imageClient = RestClient.builder()
                .baseUrl(IMAGE_BASE)
                .requestFactory(imageFactory)
                .build();
    }

    private void checkToken() {
        if (token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TMDB_API_TOKEN не настроен");
        }
    }

    private JsonNode searchTmdb(String name) {
        checkToken();

        try {
            return client.get()
                    .uri(builder -> builder
                            .path("/search/person")
                            .queryParam("query", name)
                            .queryParam("language", "ru-RU")
                            .queryParam("include_adult", false)
                            .build())
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();

            if (status == 401 || status == 403) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TMDB отклонил токен");
            }

            if (status == 429) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Лимит запросов TMDB");
            }

            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ошибка TMDB", e);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TMDB недоступен", e);
        }
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

    private String photoUrl(String path) {
        return path != null && IMAGE_PATH.matcher(path).matches() ? IMAGE_BASE + path : null;
    }

    public List<PersonOption> search(String name) {
        JsonNode response = searchTmdb(name.trim());

        if (response == null || !response.path("results").isArray()) {
            return List.of();
        }

        List<PersonOption> results = new ArrayList<>();

        for (JsonNode item : response.path("results")) {
            String url = photoUrl(item.path("profile_path").asText(null));
            if (url == null) {
                continue;
            }

            long tmdbId = item.path("id").asLong(0);
            if (tmdbId <= 0) {
                continue;
            }

            String personName = item.path("name").asText("");
            String originalName = item.path("original_name").asText("");

            if (personName.isBlank()) {
                continue;
            }

            results.add(new PersonOption(
                    tmdbId,
                    personName,
                    originalName,
                    url
            ));
        }

        String wanted = normalized(name);

        results.sort(
                Comparator.comparingInt((PersonOption p) -> {
                    String a = normalized(p.name());
                    String b = normalized(p.originalName());
                    return a.equals(wanted) || b.equals(wanted) ? 0 : 1;
                })
        );

        return results.stream().limit(12).toList();
    }

    public DownloadResult downloadAvatar(String photoUrl) {
        if (photoUrl == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ссылка на фото отсутствует");
        }

        URI uri;
        try {
            uri = URI.create(photoUrl);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неверная ссылка TMDB");
        }

        if (!"https".equals(uri.getScheme())
                || !"image.tmdb.org".equalsIgnoreCase(uri.getHost())
                || uri.getPort() != -1
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || uri.getPath() == null
                || !uri.getPath().startsWith("/t/p/w500/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Разрешены только изображения TMDB в формате w500");
        }

        String filename = uri.getPath().substring("/t/p/w500".length());
        if (!IMAGE_PATH.matcher(filename).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недопустимое имя файла изображения");
        }

        String savedName = "tmdb-person-" + filename.substring(1);
        Path destination = peopleDirectory.resolve(savedName).normalize();

        if (!destination.startsWith(peopleDirectory)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недопустимый путь изображения");
        }

        try {
            Files.createDirectories(peopleDirectory);

            if (!Files.isRegularFile(destination) || Files.size(destination) == 0) {
                byte[] content = imageClient.get()
                        .uri(filename)
                        .retrieve()
                        .body(byte[].class);

                if (content == null || content.length == 0 || content.length > MAX_IMAGE_BYTES) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TMDB вернул пустой или слишком большой файл");
                }

                Path temporary = Files.createTempFile(peopleDirectory, "tmdb-person-", ".part");

                try {
                    Files.write(temporary, content);
                    Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
        } catch (IOException | RestClientException e) {
            log.error("Не удалось скачать фото {}", photoUrl, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Не удалось скачать аватар в img/people", e);
        }

        return new DownloadResult("/img/people/" + savedName);
    }

    public BatchResult autofill(long afterId, int limit) {
        checkToken();

        List<ExistingPerson> items = jdbc.query("""
                SELECT DISTINCT p.id, p.full_name, COALESCE(p.original_name, '') AS original_name
                FROM people p
                WHERE p.id > ?
                  AND EXISTS (
                      SELECT 1
                      FROM media_credits mc
                      WHERE mc.person_id = p.id
                  )
                  AND (p.photo_url IS NULL OR BTRIM(p.photo_url) = '')
                ORDER BY p.id
                LIMIT ?
                """,
                (rs, row) -> new ExistingPerson(
                        rs.getLong("id"),
                        rs.getString("full_name"),
                        rs.getString("original_name")
                ),
                afterId,
                limit
        );

        int saved = 0;
        int skipped = 0;
        long lastId = afterId;
        List<String> savedNames = new ArrayList<>();

        for (ExistingPerson person : items) {
            lastId = person.id();

            String query = person.originalName() != null && !person.originalName().isBlank()
                    ? person.originalName()
                    : person.fullName();

            List<PersonOption> options = search(query);
            String wantedA = normalized(person.fullName());
            String wantedB = normalized(person.originalName());

            Optional<PersonOption> match = options.stream()
                    .filter(p -> normalized(p.name()).equals(wantedA)
                            || normalized(p.originalName()).equals(wantedA)
                            || (!wantedB.isBlank() && normalized(p.name()).equals(wantedB))
                            || (!wantedB.isBlank() && normalized(p.originalName()).equals(wantedB)))
                    .findFirst();

            if (match.isEmpty()) {
                skipped++;
                continue;
            }

            String localPath = downloadAvatar(match.get().photoUrl()).localPath();

            int updated = jdbc.update("""
                    UPDATE people
                    SET photo_url = ?
                    WHERE id = ?
                      AND (photo_url IS NULL OR BTRIM(photo_url) = '')
                    """,
                    localPath,
                    person.id()
            );

            if (updated > 0) {
                saved++;
                savedNames.add(person.fullName());
                log.info("TMDB avatar saved locally: personId={} path={}", person.id(), localPath);
            } else {
                skipped++;
            }
        }

        return new BatchResult(items.size(), saved, skipped, lastId, items.size() == limit, savedNames);
    }
}