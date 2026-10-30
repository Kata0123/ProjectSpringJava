
package org.example.guccispring.controller;

import org.example.guccispring.dto.Requests;
import org.example.guccispring.model.Models;
import org.example.guccispring.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping("/session")
    public Map<String, Object> session(CsrfToken csrfToken) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("csrfToken", csrfToken.getToken());

        Optional<Models.AppUser> current = service.currentUser();

        if (current.isPresent()) {
            Models.AppUser user = current.get();

            result.put("user", service.publicUser(user));
            result.put("ratings", service.repository().ratings(user.id()));
            result.put("watchlist", service.repository().watchlist(user.id()));
        } else {
            result.put("user", null);
            result.put("ratings", Map.of());
            result.put("watchlist", List.of());
        }

        return result;
    }

    @PostMapping("/auth/register")
    public Map<String, Object> register(
            @Valid @RequestBody Requests.Register body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        return service.publicUser(
                service.register(body, request, response)
        );
    }

    @PostMapping("/auth/login")
    public Map<String, Object> login(
            @Valid @RequestBody Requests.Login body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        return service.publicUser(
                service.login(body, request, response)
        );
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        new SecurityContextLogoutHandler().logout(
                request,
                response,
                authentication
        );

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me")
    public Map<String, Object> rename(
            @Valid @RequestBody Requests.Rename body,
            HttpSession session
    ) {
        return service.publicUser(service.rename(session, body));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        HttpSession session = request.getSession(false);
        service.deleteAccount(session);

        SecurityContextHolder.clearContext();

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/ratings")
    public Map<Long, Integer> ratings(HttpSession session) {
        long id = service.authenticated(session).id();
        return service.repository().ratings(id);
    }

    @PutMapping("/me/ratings/{mediaId}")
    public ResponseEntity<Void> rate(
            @PathVariable long mediaId,
            @Valid @RequestBody Requests.Rating body,
            HttpSession session
    ) {
        long id = service.authenticated(session).id();
        service.checkMedia(mediaId);
        service.repository().rate(id, mediaId, body.rating());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me/ratings/{mediaId}")
    public ResponseEntity<Void> removeRating(
            @PathVariable long mediaId,
            HttpSession session
    ) {
        long id = service.authenticated(session).id();
        service.repository().deleteRating(id, mediaId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/watchlist")
    public List<Long> watchlist(HttpSession session) {
        long id = service.authenticated(session).id();
        return service.repository().watchlist(id);
    }

    @PostMapping("/me/watchlist/{mediaId}")
    public ResponseEntity<Void> addWatchlist(
            @PathVariable long mediaId,
            HttpSession session
    ) {
        long id = service.authenticated(session).id();
        service.checkMedia(mediaId);
        service.repository().addWatchlist(id, mediaId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me/watchlist/{mediaId}")
    public ResponseEntity<Void> removeWatchlist(
            @PathVariable long mediaId,
            HttpSession session
    ) {
        long id = service.authenticated(session).id();
        service.repository().removeWatchlist(id, mediaId);
        return ResponseEntity.noContent().build();
    }
}
