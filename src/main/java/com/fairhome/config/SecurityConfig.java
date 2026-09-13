package com.fairhome.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    PasswordEncoder passwordEncoder() {
        log.debug("FairHome : SecurityConfig : in method passwordEncoder : START");
        log.info("FairHome : SecurityConfig : in method passwordEncoder : registering delegating password encoder");
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        log.debug("FairHome : SecurityConfig : in method passwordEncoder : END");
        return encoder;
    }

    @Bean
    UserDetailsService userDetailsService(FairHomeProperties properties, PasswordEncoder encoder) {
        log.debug("FairHome : SecurityConfig : in method userDetailsService : START");
        FairHomeProperties.Admin admin = properties.getAdmin();
        log.info("FairHome : SecurityConfig : in method userDetailsService : configuring in-memory admin user {}",
                admin.getUsername());
        UserDetailsService service = new InMemoryUserDetailsManager(User.builder()
                .username(admin.getUsername())
                .password(encoder.encode(admin.getPassword()))
                .roles("ADMIN")
                .build());
        log.debug("FairHome : SecurityConfig : in method userDetailsService : END");
        return service;
    }

    /** JSON admin calls and the H2 console: HTTP Basic, no HTML login page. */
    @Bean
    @Order(1)
    SecurityFilterChain apiAdminSecurity(HttpSecurity http) throws Exception {
        log.debug("FairHome : SecurityConfig : in method apiAdminSecurity : START");
        log.info("FairHome : SecurityConfig : in method apiAdminSecurity : configuring HTTP Basic security "
                + "for admin API and H2 console");
        http
                .securityMatcher("/api/admin/**", "/api/draws/**", "/h2-console/**",
                        "/exports/applications.csv", "/exports/audit.csv")
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("ADMIN"))
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        SecurityFilterChain chain = http.build();
        log.debug("FairHome : SecurityConfig : in method apiAdminSecurity : END");
        return chain;
    }

    /** Browser: form login for /admin, public pages left open. */
    @Bean
    @Order(2)
    SecurityFilterChain webSecurity(HttpSecurity http) throws Exception {
        log.debug("FairHome : SecurityConfig : in method webSecurity : START");
        log.info("FairHome : SecurityConfig : in method webSecurity : configuring form login for officer console");
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
        SecurityFilterChain chain = http.build();
        log.debug("FairHome : SecurityConfig : in method webSecurity : END");
        return chain;
    }
}
