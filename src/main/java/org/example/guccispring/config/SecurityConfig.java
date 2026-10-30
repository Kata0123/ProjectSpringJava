package org.example.guccispring.config;

import org.example.guccispring.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository users) {
        return username -> {
            var user = users.findByName(username)
                    .orElseThrow(() ->
                            new UsernameNotFoundException("Пользователь не найден"));

            return User.withUsername(user.username())
                    .password(user.passwordHash())
                    .roles(users.role(user.id()))
                    .build();
        };
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            DaoAuthenticationProvider provider
    ) {
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository
    ) throws Exception {

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/admin.html",
                                "/api/admin/**",
                                "/actuator/metrics",
                                "/actuator/metrics/**"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                "/api/me",
                                "/api/me/**",
                                "/api/auth/logout"
                        ).authenticated()

                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/catalog.html",
                                "/movie.html",
                                "/wheel.html",
                                "/profile.html",
                                "/ratings.html",
                                "/access-denied.html",
                                "/favicon.ico",
                                "/css/**",
                                "/js/**",
                                "/img/**",
                                "/error",
                                "/api/session",
                                "/api/auth/register",
                                "/api/auth/login",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/catalog",
                                "/api/media/**",
                                "/api/reference/**",
                                "/api/people/photos"
                        ).permitAll()

                        .anyRequest().denyAll()
                )

                .csrf(csrf -> csrf.csrfTokenRepository(
                        new HttpSessionCsrfTokenRepository()
                ))

                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository)
                        .requireExplicitSave(true)
                )

                .exceptionHandling(exception -> exception
                        .accessDeniedPage("/access-denied.html")
                        .authenticationEntryPoint((request, response, error) -> {
                            if (request.getRequestURI().startsWith("/api/")) {
                                response.setStatus(
                                        HttpServletResponse.SC_UNAUTHORIZED
                                );

                                response.setContentType(
                                        "application/json;charset=UTF-8"
                                );

                                response.getWriter().write(
                                        "{\"error\":\"Необходимо войти в аккаунт\"}"
                                );

                            } else {
                                response.sendRedirect(
                                        request.getContextPath()
                                                + "/access-denied.html"
                                );
                            }
                        })
                )

                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.changeSessionId())
                )

                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);

        return http.build();
    }
}