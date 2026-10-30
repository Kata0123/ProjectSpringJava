
package org.example.guccispring.service;

import org.example.guccispring.repository.CatalogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class CatalogService {

    private final CatalogRepository repository;

    public CatalogService(CatalogRepository repository) {
        this.repository = repository;
    }

    public Map<String, Object> library() {
        List<Map<String, Object>> all = repository.findAll();

        List<Map<String, Object>> featured = all.stream()
                .filter(m -> m.get("featuredPosition") != null)
                .sorted(
                        Comparator.comparingInt(
                                m -> ((Number) m.get("featuredPosition")).intValue()
                        )
                )
                .toList();

        Set<Object> featuredIds = new HashSet<>();

        featured.forEach(m -> featuredIds.add(m.get("id")));

        List<Map<String, Object>> catalog = all.stream()
                .filter(m -> !featuredIds.contains(m.get("id")))
                .toList();

        return Map.of(
                "featured", featured,
                "catalog", catalog
        );
    }

    public Map<String, Object> movie(long id) {
        Map<String, Object> m = new LinkedHashMap<>(
                repository.findById(id).orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Фильм не найден"
                        )
                )
        );

        m.put("cast", repository.people(id, "ACTOR"));
        m.put("directors", repository.people(id, "DIRECTOR"));

        @SuppressWarnings("unchecked")
        List<String> countries = (List<String>) m.get("countries");

        @SuppressWarnings("unchecked")
        List<String> studios = (List<String>) m.get("studios");

        m.put("country", String.join(", ", countries));
        m.put("studio", String.join(", ", studios));

        return m;
    }

    public List<Map<String, Object>> random(int limit) {
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Количество должно быть от 1 до 100"
            );
        }

        List<Map<String, Object>> all = new ArrayList<>(
                repository.findAll()
        );

        Collections.shuffle(all);

        return all.subList(
                0,
                Math.min(limit, all.size())
        );
    }

    public List<Map<String, Object>> reference(String name) {
        return repository.reference(name);
    }
}
