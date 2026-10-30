
package org.example.guccispring.repository;

import org.example.guccispring.model.Models;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.*;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Models.AppUser> findByName(String name) {
        var result = jdbc.query(
                """
                SELECT *
                FROM app_users
                WHERE LOWER(username) = LOWER(?)
                """,
                (rs, row) -> new Models.AppUser(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class)
                ),
                name
        );

        return result.stream().findFirst();
    }

    public Optional<Models.AppUser> findById(long id) {
        var result = jdbc.query(
                """
                SELECT *
                FROM app_users
                WHERE id = ?
                """,
                (rs, row) -> new Models.AppUser(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class)
                ),
                id
        );

        return result.stream().findFirst();
    }

    public String role(long id) {
        return jdbc.queryForObject(
                "SELECT role FROM app_users WHERE id = ?",
                String.class,
                id
        );
    }

    public long create(String name, String passwordHash) {
        Long id = jdbc.queryForObject(
                """
                INSERT INTO app_users(username, password_hash, role)
                VALUES (?, ?, 'USER')
                RETURNING id
                """,
                Long.class,
                name,
                passwordHash
        );

        return Objects.requireNonNull(id);
    }

    public void rename(long id, String name) {
        jdbc.update(
                "UPDATE app_users SET username = ? WHERE id = ?",
                name,
                id
        );
    }

    public void deleteUser(long id) {
        jdbc.update(
                "DELETE FROM app_users WHERE id = ?",
                id
        );
    }

    public Map<Long, Integer> ratings(long userId) {
        Map<Long, Integer> result = new LinkedHashMap<>();

        jdbc.query(
                """
                SELECT media_id, rating
                FROM user_ratings
                WHERE user_id = ?
                """,
                (RowCallbackHandler) rs -> result.put(
                        rs.getLong("media_id"),
                        rs.getInt("rating")
                ),
                userId
        );

        return result;
    }

    public void rate(long userId, long mediaId, int rating) {
        jdbc.update(
                """
                INSERT INTO user_ratings(user_id, media_id, rating)
                VALUES (?, ?, ?)
                ON CONFLICT (user_id, media_id)
                DO UPDATE SET rating = EXCLUDED.rating
                """,
                userId,
                mediaId,
                rating
        );
    }

    public void deleteRating(long userId, long mediaId) {
        jdbc.update(
                """
                DELETE FROM user_ratings
                WHERE user_id = ? AND media_id = ?
                """,
                userId,
                mediaId
        );
    }

    public List<Long> watchlist(long userId) {
        return jdbc.query(
                """
                SELECT media_id
                FROM watchlist
                WHERE user_id = ?
                ORDER BY added_at DESC
                """,
                (rs, row) -> rs.getLong("media_id"),
                userId
        );
    }

    public void addWatchlist(long userId, long mediaId) {
        jdbc.update(
                """
                INSERT INTO watchlist(user_id, media_id)
                VALUES (?, ?)
                ON CONFLICT (user_id, media_id) DO NOTHING
                """,
                userId,
                mediaId
        );
    }

    public void removeWatchlist(long userId, long mediaId) {
        jdbc.update(
                """
                DELETE FROM watchlist
                WHERE user_id = ? AND media_id = ?
                """,
                userId,
                mediaId
        );
    }
}
