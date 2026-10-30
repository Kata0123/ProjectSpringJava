
package org.example.guccispring.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public final class Models {
    private Models() {}

    public record AppUser(
            Long id,
            String username,
            String email,
            String passwordHash,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    public record Media(
            Long id,
            String title,
            String originalTitle,
            String mediaType,
            Integer releaseYear,
            BigDecimal externalRating,
            String overview,
            String ageRating,
            LocalDate premiereDate,
            String themeKey,
            String themeGlow,
            Boolean published
    ) {}

    public record Film(
            Long mediaId,
            String mediaType,
            Integer runtimeMinutes,
            Long budgetUsd
    ) {}

    public record Series(
            Long mediaId,
            String mediaType,
            Integer seasonsCount,
            Integer episodesCount,
            Integer episodeRuntimeMinutes,
            Integer endYear
    ) {}

    public record Genre(Long id, String name) {}

    public record MediaGenre(Long mediaId, Long genreId) {}

    public record Tag(Long id, String name) {}

    public record MediaTag(Long mediaId, Long tagId) {}

    public record Person(
            Long id,
            String fullName,
            String originalName,
            String photoUrl
    ) {}

    public record CreditRole(
            Short id,
            String code,
            String name
    ) {}

    public record MediaCredit(
            Long id,
            Long mediaId,
            Long personId,
            Short roleId,
            String characterName,
            Integer billingOrder
    ) {}

    public record Country(Long id, String name) {}

    public record MediaCountry(Long mediaId, Long countryId) {}

    public record Studio(Long id, String name) {}

    public record MediaStudio(Long mediaId, Long studioId) {}

    public record FeaturedMedia(
            Long mediaId,
            Integer position,
            Boolean active
    ) {}

    public record MediaExternalId(
            Long mediaId,
            String provider,
            String externalId
    ) {}

    public record MediaPoster(
            Long id,
            Long mediaId,
            String provider,
            String posterUrl,
            String localPath,
            String languageCode,
            Boolean isPrimary
    ) {}

    public record UserRating(
            Long userId,
            Long mediaId,
            Integer rating
    ) {}

    public record Watchlist(
            Long userId,
            Long mediaId
    ) {}
}
