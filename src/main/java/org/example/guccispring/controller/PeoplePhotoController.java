package org.example.guccispring.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/people")
public class PeoplePhotoController {

    private final JdbcTemplate jdbc;

    public PeoplePhotoController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record PersonPhoto(
            String name,
            String originalName,
            String photoUrl
    ) {}

    @GetMapping("/photos")
    public List<PersonPhoto> photos() {
        return jdbc.query(
                """
                SELECT
                    full_name,
                    original_name,
                    photo_url
                FROM people
                WHERE photo_url LIKE '/img/people/%'
                ORDER BY full_name
                """,
                (rs, rowNum) -> new PersonPhoto(
                        rs.getString("full_name"),
                        rs.getString("original_name"),
                        rs.getString("photo_url")
                )
        );
    }
}