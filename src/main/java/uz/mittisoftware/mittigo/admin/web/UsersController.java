package uz.mittisoftware.mittigo.admin.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uz.mittisoftware.mittigo.admin.backend.BackendClient;

/**
 * "Пользователи": the parents who signed in with Google (BACKEND_MVP §11) — email, name, when they joined and last signed
 * in, their Pro and how many child devices they linked; an admin can take a parent's Pro back or set its end (C46). Nothing
 * about any child is on the server (BL §18).
 */
@Controller
class UsersController {

    private static final int PAGE_SIZE = 50;

    /** As the server sends it. */
    record UserRow(
            String id,
            String email,
            String displayName,
            OffsetDateTime createdAt,
            OffsetDateTime lastSignInAt,
            boolean pro,
            boolean lifetime,
            OffsetDateTime proUntil,
            long childDevices,
            OffsetDateTime lastDeviceSyncAt
    ) {
    }

    record Users(long total, List<UserRow> items) {
    }

    /** A row as shown: times already in Tashkent time; [plan] "Pro навсегда" · "Pro до …" · "Free". */
    public record UserView(String id, String email, String name, String joined, String lastSignIn, boolean isPro, String plan, long childDevices, String lastSync) {
    }

    private final BackendClient backend;

    UsersController(BackendClient backend) {
        this.backend = backend;
    }

    @GetMapping("/users")
    String list(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        int current = Math.max(page, 0);
        Users users = backend.get(Admin.token(), "/admin/users?query={query}&limit={limit}&offset={offset}", Users.class,
                query.strip(), PAGE_SIZE, current * PAGE_SIZE);
        long pages = Math.max(1, (users.total() + PAGE_SIZE - 1) / PAGE_SIZE);

        model.addAttribute("query", query.strip());
        model.addAttribute("total", users.total());
        model.addAttribute("page", current);
        model.addAttribute("hasPrevious", current > 0);
        model.addAttribute("hasNext", current + 1 < pages);
        model.addAttribute("users", users.items().stream().map(UsersController::view).toList());

        return "users";
    }

    /** "Забрать Pro" ([action] REVOKE) or "Изменить срок" (SET: [until] `yyyy-MM-dd`, or [lifetime]) — C46. */
    @PostMapping("/users/{id}/pro")
    String changePro(
            @PathVariable String id,
            @RequestParam String action,
            @RequestParam(required = false) String until,
            @RequestParam(defaultValue = "false") boolean lifetime,
            @RequestParam(required = false) String email,
            @RequestParam(defaultValue = "") String query,
            RedirectAttributes flash
    ) {
        String who = email == null ? "" : " у " + email;
        String done = "REVOKE".equals(action) ? "Pro забран" + who + " — приложение станет Free при следующем открытии"
                : "Срок Pro" + who + " изменён";
        Map<String, Object> body = Map.of("action", action, "until", until == null ? "" : until, "lifetime", lifetime);

        Flash.run(flash, done, () -> backend.post(Admin.token(), "/admin/users/{id}/pro", body, Map.class, id));
        flash.addAttribute("query", query);

        return "redirect:/users";
    }

    private static UserView view(UserRow user) {
        return new UserView(
                user.id(),
                user.email(),
                user.displayName(),
                AuditLog.when(user.createdAt()),
                AuditLog.when(user.lastSignInAt()),
                user.pro(),
                plan(user),
                user.childDevices(),
                AuditLog.when(user.lastDeviceSyncAt()));
    }

    private static String plan(UserRow user) {
        if (user.lifetime()) return "Pro навсегда";
        if (user.pro() && user.proUntil() != null) return "Pro до " + AuditLog.when(user.proUntil());
        if (user.proUntil() != null) return "Free · Pro закончился " + AuditLog.when(user.proUntil());

        return "Free";
    }
}
