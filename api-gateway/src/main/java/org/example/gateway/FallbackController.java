
package org.example.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class FallbackController {

    private static final Logger log =
            LoggerFactory.getLogger(FallbackController.class);

    @GetMapping("/fallback/catalog")
    public Mono<ResponseEntity<Map<String, String>>> catalogFallback() {
        log.warn("Catalog service is temporarily unavailable");

        return Mono.just(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of(
                                "error",
                                "Каталог временно недоступен. Попробуйте позднее."
                        ))
        );
    }

    @GetMapping("/fallback/recommendation")
    public Mono<ResponseEntity<Map<String, String>>> recommendationFallback() {
        log.warn("Recommendation service is temporarily unavailable");

        return Mono.just(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of(
                                "error",
                                "Подборки временно недоступны. Попробуйте позднее."
                        ))
        );
    }
}
