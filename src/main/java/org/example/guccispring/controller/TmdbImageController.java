
package org.example.guccispring.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/posters")
public class TmdbImageController {

    private static final Pattern FILE_NAME =
            Pattern.compile("^[a-zA-Z0-9_-]+\\.(jpg|jpeg|png|webp)$");

    private final RestClient client;

    public TmdbImageController() {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);

        client = RestClient.builder()
                .baseUrl("https://image.tmdb.org/t/p/w500")
                .requestFactory(factory)
                .build();
    }

    @GetMapping("/{fileName:.+}")
    public ResponseEntity<byte[]> getPoster(
            @PathVariable String fileName
    ) {
        if (!FILE_NAME.matcher(fileName).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Некорректное имя постера"
            );
        }

        try {
            byte[] image = client.get()
                    .uri("/" + fileName)
                    .retrieve()
                    .body(byte[].class);

            if (image == null || image.length == 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "TMDB вернул пустое изображение"
                );
            }

            MediaType contentType;

            if (fileName.endsWith(".png")) {
                contentType = MediaType.IMAGE_PNG;
            } else if (fileName.endsWith(".webp")) {
                contentType = MediaType.parseMediaType("image/webp");
            } else {
                contentType = MediaType.IMAGE_JPEG;
            }

            return ResponseEntity.ok()
                    .contentType(contentType)
                    .cacheControl(
                            CacheControl.maxAge(7, TimeUnit.DAYS)
                                    .cachePublic()
                    )
                    .body(image);

        } catch (RestClientException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Не удалось загрузить постер из TMDB",
                    e
            );
        }
    }
}
