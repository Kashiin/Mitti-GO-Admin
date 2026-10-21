package uz.mittisoftware.mittigo.admin.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import uz.mittisoftware.mittigo.admin.backend.BackendException;

/** What every page needs (who is signed in, the current section) and what happens when the server says no. */
@ControllerAdvice
class PanelAdvice {

    @ModelAttribute
    void common(Model model, HttpServletRequest request) {
        model.addAttribute("adminLogin", Admin.login());
        model.addAttribute("path", request.getRequestURI());
    }

    /** An expired or revoked token → sign in again; anything else → a calm error page. */
    @ExceptionHandler(BackendException.class)
    String backend(BackendException e, HttpServletRequest request, Model model) {
        if (e.isUnauthorized()) {
            HttpSession session = request.getSession(false);

            SecurityContextHolder.clearContext();
            if (session != null) session.invalidate();

            return "redirect:/login?expired";
        }

        model.addAttribute("message", e.userMessage());

        return "error-page";
    }
}
