package com.theguy.app.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.theguy.app.auth.JwtAuthenticationEntryPoint;
import com.theguy.app.auth.JwtFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final JwtAuthenticationEntryPoint unauthorizedHandler;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(unauthorizedHandler))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/api/auth/**")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/auth/**")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/api/public/**")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/ws/**")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/health")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/v3/api-docs/**")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/swagger-ui/**")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/swagger-ui.html")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/h2-console/**")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/error")).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/providers/nearby").permitAll()
                .requestMatchers(HttpMethod.GET, "/providers/nearby").permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/api/search/**")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/api/search/**")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/api/jobs/**")).authenticated()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/api/providers/**")).authenticated()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/api/reviews/**")).authenticated()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/api/admin/**")).hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider());

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(
                "https://theguy.co.ke",
                "https://www.theguy.co.ke",
                "https://app.theguy.co.ke",
                "http://localhost:*",
                "http://127.0.0.1:*"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
