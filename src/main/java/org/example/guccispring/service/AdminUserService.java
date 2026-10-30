
package org.example.guccispring.service;

import org.example.guccispring.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class AdminUserService {

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AdminUserService(
            JdbcTemplate jdbc,
            UserRepository users,
            PasswordEncoder encoder
    ) {
        this.jdbc = jdbc;
        this.users = users;
        this.encoder = encoder;
    }

    public List<Map<String, Object>> findAll() {
        return jdbc.queryForList(
                """
                SELECT id, username, email, role, created_at
                FROM app_users
                ORDER BY id
                LIMIT 200
                """
        );
    }

    private long currentId() {
        String name = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        return users.findByName(name)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Пользователь не найден"
                ))
                .id();
    }

    private void validateRole(String role) {
        if (!"ADMIN".equals(role) && !"USER".equals(role)) {
            throw new IllegalArgumentException("Неизвестная роль");
        }
    }

    @Transactional
    public long create(
            String username,
            String password,
            String role
    ) {
        validateRole(role);

        String name = username.trim();

        if (users.findByName(name).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Пользователь уже существует"
            );
        }

        long id = users.create(name, encoder.encode(password));

        if ("ADMIN".equals(role)) {
            jdbc.update(
                    "UPDATE app_users SET role = 'ADMIN' WHERE id = ?",
                    id
            );
        }

        return id;
    }

    @Transactional
    public void changeRole(long id, String role) {
        validateRole(role);

        if (id == currentId() && !"ADMIN".equals(role)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Нельзя убрать роль администратора у самого себя"
            );
        }

        int updated = jdbc.update(
                "UPDATE app_users SET role = ? WHERE id = ?",
                role,
                id
        );

        if (updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Пользователь не найден"
            );
        }
    }

    @Transactional
    public void delete(long id) {
        if (id == currentId()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Нельзя удалить свой аккаунт из админ-панели"
            );
        }

        int deleted = jdbc.update(
                "DELETE FROM app_users WHERE id = ?",
                id
        );

        if (deleted == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Пользователь не найден"
            );
        }
    }
}
