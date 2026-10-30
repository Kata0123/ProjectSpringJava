
package org.example.catalog;

import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private static final Logger log =
            LoggerFactory.getLogger(CatalogController.class);

    private final CatalogRepository repository;

    public CatalogController(CatalogRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "Получить каталог и выбор редакции")
    @GetMapping("/catalog")
    public Map<String, Object> catalog() {
        log.info("Catalog requested");

        List<Map<String, Object>> all = repository.findAll();

        List<Map<String, Object>> featured = all.stream()
                .filter(m -> m.get("featuredPosition") != null)
                .sorted(Comparator.comparingInt(
                        m -> ((Number) m.get("featuredPosition")).intValue()
                ))
                .toList();

        Set<Object> featuredIds = new HashSet<>();

        featured.forEach(m -> featuredIds.add(m.get("id")));

        List<Map<String, Object>> regular = all.stream()
                .filter(m -> !featuredIds.contains(m.get("id")))
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("catalog", regular);
        result.put("featured", featured);

        return result;
    }

    @Operation(summary = "Получить подробную информацию о фильме или сериале")
    @GetMapping("/media/{id}")
    public Map<String, Object> media(@PathVariable long id) {
        log.info("Media requested id={}", id);

        Map<String, Object> result = new LinkedHashMap<>(
                repository.findById(id).orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Фильм или сериал не найден"
                        )
                )
        );

        List<String> actors = repository.people(id, "ACTOR");
        List<String> directors = repository.people(id, "DIRECTOR");

        @SuppressWarnings("unchecked")
        List<String> countries = (List<String>) result.get("countries");

        @SuppressWarnings("unchecked")
        List<String> studios = (List<String>) result.get("studios");

        result.put("cast", actors);
        result.put("directors", directors);
        result.put("country", String.join(", ", countries));
        result.put("studio", String.join(", ", studios));

        return result;
    }

    @Operation(summary = "Получить справочник")
    @GetMapping("/reference/{name}")
    public List<Map<String, Object>> reference(@PathVariable String name) {
        log.info("Reference requested name={}", name);

        try {
            return repository.reference(name);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Неизвестный справочник"
            );
        }
    }
}
