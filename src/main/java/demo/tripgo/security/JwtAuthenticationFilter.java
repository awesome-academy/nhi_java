package demo.tripgo.security;

import demo.tripgo.entity.User;
import demo.tripgo.entity.UserStatus;
import io.jsonwebtoken.JwtException;
import demo.tripgo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter
        extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        Long userId = null;
        try {
            // Parse and validate signature, expiry and required userId in one call.
            userId = jwtService.extractUserId(token);
        } catch (JwtException | IllegalArgumentException exception) {
            // Invalid tokens leave the request unauthenticated.
            log.debug("Invalid JWT: {}", exception.getMessage());
        }

        // Authorization rules decide whether anonymous access is allowed.
        if (userId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        User user = userRepository
            .findById(userId)
            .orElse(null);

        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            filterChain.doFilter(request, response);
            return;
        }

        var authority = new SimpleGrantedAuthority(
            "ROLE_" + user.getRole().name()
        );

        var authentication = new UsernamePasswordAuthenticationToken(
            user,
            null,
            List.of(authority)
        );

        SecurityContextHolder
            .getContext()
            .setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
