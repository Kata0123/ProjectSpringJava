
package org.example.guccispring.controller;

import org.example.guccispring.service.AdminUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    public record CreateUser(
            @NotBlank
            @Size(min = 2, max = 80)
            String username,

            @NotBlank
            @Size(min = 8, max = 72)
            String password,

            @NotBlank
            @Pattern(regexp = "USER|ADMIN")
            String role
    ) {}

    public record ChangeRole(
            @NotBlank
            @Pattern(regexp = "USER|ADMIN")
            String role
    ) {}

    @GetMapping
    public List<Map<String, Object>> users() {
        return service.findAll();
    }

    @PostMapping
    public Map<String, Long> create(
            @Valid @RequestBody CreateUser body
    ) {
        long id = service.create(
                body.username(),
                body.password(),
                body.role()
        );

        return Map.of("id", id);
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<Void> changeRole(
            @PathVariable long id,
            @Valid @RequestBody ChangeRole body
    ) {
        service.changeRole(id, body.role());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
