package uz.mittisoftware.mittigo.admin.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class LoginController {

    @GetMapping("/login")
    String login(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            @RequestParam(required = false) String expired,
            HttpSession session,
            Model model
    ) {
        if (error != null) model.addAttribute("error", SecurityConfig.lastError(session));
        if (logout != null) model.addAttribute("info", "Вы вышли из админки");
        if (expired != null) model.addAttribute("info", "Сессия закончилась — войдите снова");

        return "login";
    }
}
