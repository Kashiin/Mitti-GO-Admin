package uz.mittisoftware.mittigo.admin.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import uz.mittisoftware.mittigo.admin.security.AdminAuthentication;

/** The signed-in admin of the current request. */
final class Admin {

    private Admin() {
    }

    static String token() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        return authentication instanceof AdminAuthentication admin ? admin.token() : null;
    }

    static String login() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        return authentication instanceof AdminAuthentication admin ? admin.getName() : null;
    }
}
