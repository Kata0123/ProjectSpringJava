package org.example.guccispring.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class PosterResourceConfig implements WebMvcConfigurer {

    @Value("${posters.storage-dir:./src/main/resources/static/img/posters}")
    private String postersStorageDir;

    @Value("${people.storage-dir:./src/main/resources/static/img/people}")
    private String peopleStorageDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String postersLocation = Path.of(postersStorageDir)
                .toAbsolutePath()
                .normalize()
                .toUri()
                .toString();

        String peopleLocation = Path.of(peopleStorageDir)
                .toAbsolutePath()
                .normalize()
                .toUri()
                .toString();

        registry.addResourceHandler("/img/posters/**")
        .addResourceLocations(
                postersLocation.endsWith("/") ? postersLocation : postersLocation + "/",
                "classpath:/static/img/posters/"
        )
        .setCachePeriod(3600);

        registry.addResourceHandler("/img/people/**")
        .addResourceLocations(
                peopleLocation.endsWith("/") ? peopleLocation : peopleLocation + "/",
                "classpath:/static/img/people/"
        )
        .setCachePeriod(3600);
    }
}
