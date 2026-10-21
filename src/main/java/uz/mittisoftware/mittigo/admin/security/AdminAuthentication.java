package uz.mittisoftware.mittigo.admin.security;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** A signed-in admin: their login and the server's admin token, held only in the server-side session. */
public class AdminAuthentication extends AbstractAuthenticationToken {

    private final String login;
    private final transient String token;

    AdminAuthentication(String login, String token) {
        super(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        this.login = login;
        this.token = token;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return login;
    }

    public String token() {
        return token;
    }
}
