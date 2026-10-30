
package org.example.guccispring.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TitleRequest(
        @NotBlank
        @Size(max = 300)
        String title,

        @Size(max = 300)
        String originalTitle,

        @NotBlank
        @Pattern(regexp = "MOVIE|SERIES")
        String mediaType,

        @NotNull
        @Min(1888)
        @Max(2100)
        Integer releaseYear,

        @DecimalMin("0.0")
        @DecimalMax("10.0")
        BigDecimal externalRating,

        @Size(max = 5000)
        String overview,

        @Size(max = 20)
        String ageRating,

        LocalDate premiereDate,

        @Size(max = 30)
        String themeKey,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
        String themeGlow,

        @Positive
        Integer runtimeMinutes,

        @PositiveOrZero
        Long budgetUsd,

        @Positive
        Integer seasonsCount,

        @Positive
        Integer episodesCount,

        @Positive
        Integer episodeRuntimeMinutes,

        @Min(1888)
        @Max(2100)
        Integer endYear,

        @Size(max = 30)
        List<@NotBlank @Size(max = 80) String> genres,

        @Size(max = 30)
        List<@NotBlank @Size(max = 80) String> tags,

        @Size(max = 30)
        List<@NotBlank @Size(max = 120) String> countries,

        @Size(max = 30)
        List<@NotBlank @Size(max = 200) String> studios,

        @Size(max = 100)
        List<@NotBlank @Size(max = 200) String> actors,

        @Size(max = 30)
        List<@NotBlank @Size(max = 200) String> directors,

        @Size(max = 2000)
        String posterUrl,

        Boolean featured
) {
}
