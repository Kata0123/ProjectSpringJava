
package org.example.guccispring.controller;

import org.example.guccispring.service.CatalogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping("/catalog")
    public Map<String, Object> catalog() {
        return service.library();
    }

    @GetMapping("/media/{id}")
    public Map<String, Object> media(@PathVariable long id) {
        return service.movie(id);
    }

    @GetMapping("/media/random")
    public List<Map<String, Object>> random(
            @RequestParam(defaultValue = "50") int limit) {
        return service.random(limit);
    }

    @GetMapping("/reference/{name}")
    public List<Map<String, Object>> reference(@PathVariable String name) {
        return service.reference(name);
    }
}
