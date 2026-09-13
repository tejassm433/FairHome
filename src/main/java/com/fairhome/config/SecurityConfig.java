package com.fairhome.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Gates the officer console and every write that can change the register or the draw.
 *
 * <p>The public apply / status / rules / results surfaces stay open. One in-memory user is enough
 * for a single-jar deployment: username and password come from {@code application.properties}.
 * The browser uses a sign-in form; the JSON admin API accepts the same username and password
 * via HTTP Basic.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(FairHomeProperties properties, PasswordEncoder encoder) {
        FairHomeProperties.Admin admin = properties.getAdmin();
        return new InMemoryUserDetailsManager(User.builder()
                .username(admin.getUsername())
                .password(encoder.encode(admin.getPassword()))
                .roles("ADMIN")
                .build());
    }

    /** JSON admin calls and the H2 console: HTTP Basic, no HTML login page. */
    @Bean
    @Order(1)
    SecurityFilterChain apiAdminSecurity(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/admin/**", "/api/draws/**", "/h2-console/**",
                        "/exports/applications.csv", "/exports/audit.csv")
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("ADMIN"))
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }

    /** Browser: form login for /admin, public pages left open. */
    @Bean
    @Order(2)
    SecurityFilterChain webSecurity(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/error").permitAll()
                        .requestMatchers("/", "/apply", "/status", "/rules", "/verify", "/results")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/apply", "/status").permitAll()
                        .requestMatchers("/admin/login").permitAll()
                        .requestMatchers(
                                "/api/applications",
                                "/api/applications/*/status",
                                "/api/rules",
                                "/api/rules/history",
                                "/api/results",
                                "/api/verify")
                        .permitAll()
                        .requestMatchers("/exports/draw/**", "/exports/rules/**").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .formLogin(form -> form
                        .loginPage("/admin/login")
                        .loginProcessingUrl("/admin/login")
                        .defaultSuccessUrl("/admin", true)
                        .failureUrl("/admin/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/admin/login?loggedOut")
                        .permitAll());
        return http.build();
    }
}
