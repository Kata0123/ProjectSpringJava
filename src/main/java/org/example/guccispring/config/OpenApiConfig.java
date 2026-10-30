
package org.example.guccispring.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI mainServiceApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Кадр — User & Admin API")
                        .version("1.0.0")
                        .description("Авторизация, пользователи, оценки, watchlist и административный CRUD"));
    }
}
