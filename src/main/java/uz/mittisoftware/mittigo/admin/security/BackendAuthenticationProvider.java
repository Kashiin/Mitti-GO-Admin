package uz.mittisoftware.mittigo.admin.security;

import java.util.Map;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import uz.mittisoftware.mittigo.admin.backend.BackendClient;
import uz.mittisoftware.mittigo.admin.backend.BackendException;

/**
 * Checks the login form against the server (`POST /admin/auth/login`): the server owns the passwords, the lockout after 5
 * failures and the 8-hour token. The panel only keeps that token in the session.
 */
@Component
class BackendAuthenticationProvider implements AuthenticationProvider {

    private final BackendClient backend;

    BackendAuthenticationProvider(BackendClient backend) {
        this.backend = backend;
    }

    record LoginOut(String token, String expiresAt, String login) {
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String login = authentication.getName();
        String password = authentication.getCredentials() == null ? "" : authentication.getCredentials().toString();

        try {
            LoginOut session = backend.post(null, "/admin/auth/login", Map.of("login", login, "password", password), LoginOut.class);

            return new AdminAuthentication(session.login(), session.token());
        } catch (BackendException e) {
            if (e.isUnauthorized()) throw new BadCredentialsException(e.userMessage());

            throw new AuthenticationServiceException(e.userMessage());
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
