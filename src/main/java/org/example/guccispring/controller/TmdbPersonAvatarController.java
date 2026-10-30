package org.example.guccispring.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.example.guccispring.service.TmdbPersonAvatarService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/admin/tmdb/people")
public class TmdbPersonAvatarController {

    private final TmdbPersonAvatarService service;

    public TmdbPersonAvatarController(TmdbPersonAvatarService service) {
        this.service = service;
    }

    public record DownloadRequest(String photoUrl) {}

    @Operation(summary = "Поиск актёров и режиссёров через TMDB")
    @GetMapping("/search")
    public List<TmdbPersonAvatarService.PersonOption> search(
            @RequestParam @NotBlank @Size(max = 200) String name
    ) {
        return service.search(name);
    }

    @Operation(summary = "Скачать выбранную аватарку в локальную папку")
    @PostMapping("/download")
    public TmdbPersonAvatarService.DownloadResult download(@RequestBody DownloadRequest request) {
        return service.downloadAvatar(request.photoUrl());
    }

    @Operation(summary = "Заполнить отсутствующие аватарки локальными файлами")
    @PostMapping("/autofill")
    public TmdbPersonAvatarService.BatchResult autofill(
            @RequestParam(defaultValue = "0") @Min(0) long afterId,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit
    ) {
        return service.autofill(afterId, limit);
    }
}