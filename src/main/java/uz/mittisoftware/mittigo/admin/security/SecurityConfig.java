package uz.mittisoftware.mittigo.admin.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.WebAttributes;
import uz.mittisoftware.mittigo.admin.backend.BackendClient;
import uz.mittisoftware.mittigo.admin.backend.BackendException;

/**
 * Form sign-in with CSRF protection and a fresh session after sign-in. Sign-out also ends the admin token on the server.
 * Pages can't be framed (clickjacking).
 */
@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain adminSecurity(HttpSecurity http, BackendAuthenticationProvider provider, BackendClient backend) throws Exception {
        return http
                .authenticationProvider(provider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/js/**", "/favicon.svg", "/error").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .failureHandler((request, response, exception) -> {
                            HttpSession session = request.getSession();
                            session.setAttribute(WebAttributes.AUTHENTICATION_EXCEPTION, exception);
                            response.sendRedirect("/login?error");
                        }))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .addLogoutHandler((request, response, authentication) -> {
                            if (authentication instanceof AdminAuthentication admin) endServerSession(backend, admin);
                        })
                        .logoutSuccessUrl("/login?logout"))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; img-src 'self' https: data:; style-src 'self' https://fonts.googleapis.com; "
                                        + "font-src https://fonts.gstatic.com; form-action 'self'; frame-ancestors 'none'")))
                .build();
    }

    private static void endServerSession(BackendClient backend, AdminAuthentication admin) {
        try {
            backend.post(admin.token(), "/admin/auth/logout", null, Void.class);
        } catch (BackendException ignored) {
            // The token expires on its own anyway.
        }
    }

    /** Message of the last failed sign-in, for the login page. */
    static String lastError(HttpSession session) {
        Object error = session == null ? null : session.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);

        return error instanceof AuthenticationException e ? e.getMessage() : null;
    }
}
