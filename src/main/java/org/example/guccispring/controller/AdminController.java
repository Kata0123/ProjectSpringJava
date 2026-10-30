
package org.example.guccispring.controller;

import org.example.guccispring.service.AdminService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@Validated
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    @GetMapping("/{table}")
    public List<Map<String, Object>> all(
            @PathVariable String table,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset
    ) {
        return service.all(table, limit, offset);
    }

    @GetMapping("/{table}/{id}")
    public Map<String, Object> one(
            @PathVariable String table,
            @PathVariable String id
    ) {
        return service.one(table, id);
    }

    @PostMapping("/{table}")
    public Map<String, Object> create(
            @PathVariable String table,
            @RequestBody Map<String, Object> body
    ) {
        return service.create(table, body);
    }

    @PutMapping("/{table}/{id}")
    public Map<String, Object> update(
            @PathVariable String table,
            @PathVariable String id,
            @RequestBody Map<String, Object> body
    ) {
        return service.update(table, id, body);
    }

    @DeleteMapping("/{table}/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String table,
            @PathVariable String id
    ) {
        service.delete(table, id);
        return ResponseEntity.noContent().build();
    }
}
