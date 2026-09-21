package dev.fiyin.orderflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean
    UserDetailsService users(@Value("${orderflow.demo-password}") String password, PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
            User.withUsername("student").password(encoder.encode(password)).roles("SHOPPER").build(),
            User.withUsername("friend").password(encoder.encode(password)).roles("SHOPPER").build(),
            User.withUsername("operator").password(encoder.encode(password)).roles("OPERATOR").build());
    }
    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/admin/**").hasRole("OPERATOR")
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form.defaultSuccessUrl("/", true))
            .exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(
                (request, response, exception) -> response.sendError(401),
                request -> request.getRequestURI().startsWith("/api/")))
            .logout(logout -> logout.logoutSuccessUrl("/login"))
            // Leave CSRF protection ON. The browser fetches a token before sending changes.
            .build();
    }
}
