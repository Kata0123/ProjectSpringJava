
package org.example.guccispring.dto;

import jakarta.validation.constraints.*;

public final class Requests {
    private Requests() {}

    public record Register(
            @NotBlank(message = "Введите имя")
            @Size(min = 2, max = 80, message = "Имя должно содержать от 2 до 80 символов")
            String name,

            @NotBlank(message = "Введите пароль")
            @Size(min = 8, max = 72, message = "Пароль должен содержать от 8 до 72 символов")
            String password
    ) {}

    public record Login(
            @NotBlank(message = "Введите имя") String name,
            @NotBlank(message = "Введите пароль") String password
    ) {}

    public record Rename(
            @NotBlank(message = "Введите имя")
            @Size(min = 2, max = 80)
            String name
    ) {}

    public record Rating(
            @NotNull(message = "Укажите оценку")
            @Min(value = 1, message = "Минимальная оценка — 1")
            @Max(value = 10, message = "Максимальная оценка — 10")
            Integer rating
    ) {}
}
