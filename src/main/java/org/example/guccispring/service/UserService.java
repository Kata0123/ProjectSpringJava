
package org.example.guccispring.service;

import org.example.guccispring.dto.Requests;
import org.example.guccispring.model.Models;
import org.example.guccispring.repository.CatalogRepository;
import org.example.guccispring.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository users;
    private final CatalogRepository catalog;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy holder =
            SecurityContextHolder.getContextHolderStrategy();

    public UserService(
            UserRepository users,
            CatalogRepository catalog,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository
    ) {
        this.users = users;
        this.catalog = catalog;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    public Map<String, Object> publicUser(Models.AppUser user) {
        return Map.of(
                "id", user.id(),
                "name", user.username(),
                "role", users.role(user.id())
        );
    }

    public Optional<Models.AppUser> currentUser() {
        Authentication auth = holder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }

        return users.findByName(auth.getName());
    }

    public Models.AppUser authenticated(HttpSession session) {
        return currentUser().orElseThrow(() ->
                new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Сначала войдите в аккаунт"
                )
        );
    }

    private void establishSession(
            String name,
            String password,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        Authentication authentication;

        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            name,
                            password
                    )
            );
        } catch (org.springframework.security.core.AuthenticationException e) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Неверное имя или пароль"
            );
        }

        request.getSession(true);
        request.changeSessionId();

        SecurityContext context = holder.createEmptyContext();
        context.setAuthentication(authentication);
        holder.setContext(context);

        securityContextRepository.saveContext(
                context,
                request,
                response
        );
    }

    public Models.AppUser register(
            Requests.Register body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String name = body.name().trim();

        if (users.findByName(name).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Это имя уже занято"
            );
        }

        long id = users.create(
                name,
                passwordEncoder.encode(body.password())
        );

        establishSession(name, body.password(), request, response);

        return users.findById(id).orElseThrow();
    }

    public Models.AppUser login(
            Requests.Login body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        establishSession(
                body.name().trim(),
                body.password(),
                request,
                response
        );

        return currentUser().orElseThrow(() ->
                new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Ошибка входа"
                )
        );
    }

    public Models.AppUser rename(
            HttpSession session,
            Requests.Rename body
    ) {
        Models.AppUser user = authenticated(session);
        String name = body.name().trim();

        users.findByName(name).ifPresent(other -> {
            if (!other.id().equals(user.id())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Это имя уже занято"
                );
            }
        });

        users.rename(user.id(), name);

        Authentication authentication = holder.getContext().getAuthentication();

        if (authentication != null &&
                authentication.getPrincipal() instanceof
                        org.springframework.security.core.userdetails.UserDetails details) {
            var updatedDetails =
                    org.springframework.security.core.userdetails.User
                            .withUserDetails(details)
                            .username(name)
                            .build();

            Authentication updated =
                    new UsernamePasswordAuthenticationToken(
                            updatedDetails,
                            authentication.getCredentials(),
                            updatedDetails.getAuthorities()
                    );

            holder.getContext().setAuthentication(updated);
        }

        return users.findById(user.id()).orElseThrow();
    }

    public void deleteAccount(HttpSession session) {
        Models.AppUser user = authenticated(session);
        users.deleteUser(user.id());
        holder.clearContext();
        session.invalidate();
    }

    public void checkMedia(long mediaId) {
        if (mediaId < 1 || !catalog.exists(mediaId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Фильм не найден"
            );
        }
    }

    public UserRepository repository() {
        return users;
    }
}
