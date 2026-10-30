
package org.example.guccispring.controller;

import org.example.guccispring.dto.TitleRequest;
import org.example.guccispring.service.AdminTitleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/titles")
public class AdminTitleController {

    private final AdminTitleService service;

    public AdminTitleController(AdminTitleService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public Map<String, Long> create(
            @Valid @RequestBody TitleRequest body
    ) {
        return Map.of("id", service.create(body));
    }

    @PutMapping("/{id}")
    public Map<String, Long> update(
            @PathVariable long id,
            @Valid @RequestBody TitleRequest body
    ) {
        service.update(id, body);
        return Map.of("id", id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
