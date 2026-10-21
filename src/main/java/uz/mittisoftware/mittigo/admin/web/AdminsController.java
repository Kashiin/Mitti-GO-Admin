package uz.mittisoftware.mittigo.admin.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uz.mittisoftware.mittigo.admin.backend.BackendClient;
import uz.mittisoftware.mittigo.admin.web.Models.AdminAccount;
import uz.mittisoftware.mittigo.admin.web.Models.Items;

/**
 * "Администраторы": the admin-panel accounts. The main admin (from the server secrets) adds one (login + starting
 * password), turns one off or back on, or deletes it; the main admin itself can't be changed here. Each admin's changes
 * are signed with their login in the change log.
 */
@Controller
class AdminsController {

    private static final ParameterizedTypeReference<Items<AdminAccount>> LIST = new ParameterizedTypeReference<>() {
    };
    // The server's rules, checked here first so the message is in Russian.
    private static final Pattern LOGIN = Pattern.compile("[A-Za-z0-9._-]{3,50}");
    private static final int MIN_PASSWORD_LENGTH = 12;

    /** An account as listed: [lastLogin] already in Tashkent time. */
    public record AdminView(UUID id, String login, boolean isActive, boolean isMe, boolean isMain, String lastLogin) {
    }

    private final BackendClient backend;

    AdminsController(BackendClient backend) {
        this.backend = backend;
    }

    @GetMapping("/admins")
    String list(Model model) {
        List<AdminAccount> accounts = backend.get(Admin.token(), "/admin/admins", LIST).items();

        // Only the main admin adds, turns off and deletes the others.
        model.addAttribute("canManage", accounts.stream().anyMatch(a -> a.isMe() && a.isMain()));
        model.addAttribute("admins", accounts.stream()
                .map(a -> new AdminView(a.id(), a.login(), a.isActive(), a.isMe(), a.isMain(), AuditLog.when(a.lastLoginAt())))
                .toList());

        return "admins";
    }

    @PostMapping("/admins")
    String create(
            @RequestParam String login,
            @RequestParam String password,
            @RequestParam String repeatPassword,
            RedirectAttributes flash
    ) {
        String name = login.strip();
        String problem = !LOGIN.matcher(name).matches() ? "Логин: от 3 до 50 символов — латинские буквы, цифры, точка, _ или -"
                : password.length() < MIN_PASSWORD_LENGTH ? "Пароль: минимум " + MIN_PASSWORD_LENGTH + " символов"
                : !password.equals(repeatPassword) ? "Пароль и повтор не совпадают"
                : null;

        if (problem != null) {
            flash.addFlashAttribute("error", problem);
            flash.addFlashAttribute("login", name);
            return "redirect:/admins";
        }

        Flash.run(flash, "Администратор «" + name + "» создан. Передайте ему логин и пароль лично.", () -> backend.post(
                Admin.token(), "/admin/admins", Map.of("login", name, "password", password), AdminAccount.class));

        return "redirect:/admins";
    }

    @PostMapping("/admins/{id}/delete")
    String delete(@PathVariable UUID id, @RequestParam String login, RedirectAttributes flash) {
        Flash.run(flash, "Администратор «" + login + "» удалён", () -> backend.delete(Admin.token(), "/admin/admins/{id}", id));

        return "redirect:/admins";
    }

    @PostMapping("/admins/{id}/active")
    String setActive(@PathVariable UUID id, @RequestParam boolean active, @RequestParam String login, RedirectAttributes flash) {
        Flash.run(flash, active ? "«" + login + "» снова может входить" : "«" + login + "» отключён, его сессии завершены",
                () -> backend.put(Admin.token(), "/admin/admins/{id}", Map.of("isActive", active), Void.class, id));

        return "redirect:/admins";
    }
}
