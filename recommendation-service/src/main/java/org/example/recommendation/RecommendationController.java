
package org.example.recommendation;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RecommendationController {

    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    @Operation(summary = "Получить случайные фильмы и сериалы для колеса удачи")
    @GetMapping("/media/random")
    public List<Map<String, Object>> random(
            @RequestParam(defaultValue = "50") int limit
    ) {
        return service.random(limit);
    }

    @Operation(summary = "Получить фильмы и сериалы с похожими жанрами и тегами")
    @GetMapping("/recommendations/similar/{id}")
    public List<Map<String, Object>> similar(
            @PathVariable long id,
            @RequestParam(defaultValue = "6") int limit
    ) {
        return service.similar(id, limit);
    }
}
