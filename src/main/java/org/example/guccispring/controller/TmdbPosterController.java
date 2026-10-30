package org.example.guccispring.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.*;
import org.example.guccispring.service.TmdbPosterService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/admin/tmdb")
public class TmdbPosterController {

    private final TmdbPosterService service;

    public TmdbPosterController(TmdbPosterService service) {
        this.service = service;
    }

    public record DownloadRequest(String posterUrl) {}

    @Operation(summary = "Поиск постеров через TMDB")
    @GetMapping("/search")
    public List<TmdbPosterService.PosterOption> search(
            @RequestParam @NotBlank @Size(max = 200) String title,
            @RequestParam @Pattern(regexp = "MOVIE|SERIES") String type,
            @RequestParam(required = false)
            @Min(1888) @Max(2100) Integer year
    ) {
        return service.search(title, type, year);
    }

    @Operation(summary = "Скачать выбранный постер в локальную папку")
    @PostMapping("/download")
    public TmdbPosterService.DownloadResult download(
            @RequestBody DownloadRequest request
    ) {
        return service.downloadPoster(request.posterUrl());
    }

    @Operation(summary = "Заполнить отсутствующие постеры локальными файлами")
    @PostMapping("/autofill")
    public TmdbPosterService.BatchResult autofill(
            @RequestParam(defaultValue = "0") @Min(0) long afterId,
            @RequestParam(defaultValue = "5") @Min(1) @Max(10) int limit
    ) {
        return service.autofill(afterId, limit);
    }
}