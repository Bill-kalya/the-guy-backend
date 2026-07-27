package com.theguy.app.auth;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final StringRedisTemplate redisTemplate;

    public JwtFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService, StringRedisTemplate redisTemplate) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                   @NonNull HttpServletResponse response,
                                   @NonNull FilterChain chain)
            throws ServletException, IOException {
        
        log.info("REQUEST PATH: {}", request.getRequestURI());
        final String authorizationHeader = request.getHeader("Authorization");
        
        String userId = null;
        String jwt = null;
        
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            try {
                String jti = jwtUtil.getTokenId(jwt);
                String blacklistKey = "token_blacklist:" + jti;
                try {
                    if (Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey))) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"success\":false,\"message\":\"Token has been revoked\"}");
                        return;
                    }
                } catch (RuntimeException redisEx) {
                    log.warn("Redis unavailable for token blacklist check: {}", redisEx.getMessage());
                }

                userId = jwtUtil.extractUserId(jwt);
                
                try {
                    io.jsonwebtoken.Claims claims = jwtUtil.getClaimsFromToken(jwt);
                    String impersonatorId = claims.get("impersonator_id", String.class);
                    if (impersonatorId != null) {
                        request.setAttribute("impersonatorId", impersonatorId);
                        log.debug("Impersonation token detected. Impersonator: {}, Target: {}", impersonatorId, userId);
                    }
                } catch (Exception ignored) {}
            } catch (ExpiredJwtException e) {
                log.warn("JWT token expired for path: {} — allowing request to proceed (Spring Security will enforce auth)", request.getRequestURI());
                // Don't reject — let Spring Security's authorization rules decide
                // Public endpoints (permitAll) will still work
                // Protected endpoints will get 401 from the AuthenticationEntryPoint
            } catch (MalformedJwtException e) {
                log.warn("Invalid JWT token for path: {}", request.getRequestURI());
                // Don't reject — same as above
            } catch (SignatureException e) {
                log.warn("JWT signature validation failed for path: {}", request.getRequestURI());
            } catch (Exception e) {
                log.error("JWT authentication error: {}", e.getMessage());
            }
        }
        
        if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails;
            try {
                userDetails = this.userDetailsService.loadUserByUsername(userId);
            } catch (UsernameNotFoundException e) {
                log.warn("User not found for token: {}", userId);
                // Don't reject — let Spring Security handle it
                chain.doFilter(request, response);
                return;
            }
            
            if (jwtUtil.validateToken(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken authToken = 
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.debug("Authenticated user: {}", userId);
            } else {
                log.warn("Invalid token for user: {}", userId);
                // Don't reject — let Spring Security handle it
            }
        }
        
        chain.doFilter(request, response);
    }
    
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        return path.startsWith("/api/auth/") ||
               path.startsWith("/auth/") ||
               path.startsWith("/api/public/") || 
               path.startsWith("/ws/") || 
               path.startsWith("/api/platform/") ||
               path.startsWith("/api/categories/") ||
               path.startsWith("/api/search/") ||
               path.startsWith("/actuator/health") ||
               path.startsWith("/v3/api-docs/") ||
               path.startsWith("/swagger-ui/") ||
               path.equals("/swagger-ui.html") ||
               path.startsWith("/h2-console/") ||
               path.equals("/error");
    }
}
