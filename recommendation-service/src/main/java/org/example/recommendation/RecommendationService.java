
package org.example.recommendation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class RecommendationService {

    private static final Logger log =
            LoggerFactory.getLogger(RecommendationService.class);

    private final RestClient client;
    private final CircuitBreakerFactory<?, ?> breakers;

    public RecommendationService(
            RestClient.Builder builder,
            CircuitBreakerFactory<?, ?> breakers
    ) {
        this.client = builder.build();
        this.breakers = breakers;
    }

    private List<Map<String, Object>> catalog() {
        return breakers.create("catalogLookup").run(
                () -> {
                    Map<String, List<Map<String, Object>>> response =
                            client.get()
                                    .uri("http://CATALOG-SERVICE/api/catalog")
                                    .retrieve()
                                    .body(new ParameterizedTypeReference<>() {});

                    if (response == null) {
                        throw new IllegalStateException("Каталог вернул пустой ответ");
                    }

                    List<Map<String, Object>> all = new ArrayList<>();

                    all.addAll(response.getOrDefault("catalog", List.of()));
                    all.addAll(response.getOrDefault("featured", List.of()));

                    return all;
                },
                error -> {
                    log.error(
                            "Catalog service request failed: {}",
                            error.toString()
                    );

                    throw new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "Сервис каталога временно недоступен"
                    );
                }
        );
    }

    public List<Map<String, Object>> random(int limit) {
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Количество должно быть от 1 до 100"
            );
        }

        log.info("Random media requested limit={}", limit);

        List<Map<String, Object>> items = catalog();

        Collections.shuffle(items);

        return items.subList(
                0,
                Math.min(limit, items.size())
        );
    }

    private Set<String> values(Object object) {
        if (!(object instanceof List<?> list)) {
            return Set.of();
        }

        Set<String> result = new HashSet<>();

        for (Object value : list) {
            if (value != null) {
                result.add(value.toString());
            }
        }

        return result;
    }

    private int score(
            Map<String, Object> source,
            Map<String, Object> candidate
    ) {
        Set<String> sourceGenres = values(source.get("genres"));
        Set<String> sourceTags = values(source.get("tags"));

        Set<String> candidateGenres = values(candidate.get("genres"));
        Set<String> candidateTags = values(candidate.get("tags"));

        int total = 0;

        for (String genre : candidateGenres) {
            if (sourceGenres.contains(genre)) {
                total++;
            }
        }

        for (String tag : candidateTags) {
            if (sourceTags.contains(tag)) {
                total += 2;
            }
        }

        return total;
    }

    public List<Map<String, Object>> similar(long id, int limit) {
        if (limit < 1 || limit > 30) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Количество должно быть от 1 до 30"
            );
        }

        List<Map<String, Object>> items = catalog();

        Map<String, Object> source = items.stream()
                .filter(m -> Objects.equals(
                        ((Number) m.get("id")).longValue(),
                        id
                ))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Фильм не найден"
                ));

        return items.stream()
                .filter(m -> ((Number) m.get("id")).longValue() != id)
                .filter(m -> score(source, m) > 0)
                .sorted((a, b) -> Integer.compare(
                        score(source, b),
                        score(source, a)
                ))
                .limit(limit)
                .toList();
    }
}
