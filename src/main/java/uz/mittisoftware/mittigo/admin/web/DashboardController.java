package uz.mittisoftware.mittigo.admin.web;

import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uz.mittisoftware.mittigo.admin.backend.BackendClient;
import uz.mittisoftware.mittigo.admin.web.Models.AuditEntry;
import uz.mittisoftware.mittigo.admin.web.Models.Items;
import uz.mittisoftware.mittigo.admin.web.Models.Quota;
import uz.mittisoftware.mittigo.admin.web.Models.Stats;

/** Overview (counts only — nothing about what any child watches), the change log and the admin's own password. */
@Controller
class DashboardController {

    private static final ParameterizedTypeReference<Items<AuditEntry>> AUDIT = new ParameterizedTypeReference<>() {
    };
    private static final int RECENT_AUDIT = 8;
    private static final int FULL_AUDIT = 200;
    private static final int RECENT_CALLS = 30;

    private final BackendClient backend;

    DashboardController(BackendClient backend) {
        this.backend = backend;
    }

    @GetMapping("/")
    String dashboard(Model model) {
        model.addAttribute("stats", backend.get(Admin.token(), "/admin/stats", Stats.class));

        Quota quota = backend.get(Admin.token(), "/admin/quota?recent={recent}", Quota.class, RECENT_CALLS);

        model.addAttribute("quotaUsage", QuotaLog.usage(quota));
        model.addAttribute("quotaCalls", QuotaLog.recent(quota));
        model.addAttribute("audit", AuditLog.lines(backend.get(Admin.token(), "/admin/audit?limit={limit}", AUDIT, RECENT_AUDIT).items()));

        return "dashboard";
    }

    /** The log, newest first; [group] is a filter tab (an action-code prefix from [AuditLog#GROUPS]). */
    @GetMapping("/audit")
    String audit(@RequestParam(defaultValue = "") String group, Model model) {
        String prefix = AuditLog.GROUPS.containsKey(group) ? group : "";

        model.addAttribute("groups", AuditLog.GROUPS);
        model.addAttribute("group", prefix);
        model.addAttribute("audit", AuditLog.lines(backend.get(Admin.token(), "/admin/audit?limit={limit}&action={action}", AUDIT, FULL_AUDIT, prefix).items()));

        return "audit";
    }

    @GetMapping("/account")
    String account() {
        return "account";
    }

    @PostMapping("/account/password")
    String password(
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String repeatPassword,
            RedirectAttributes flash
    ) {
        if (!newPassword.equals(repeatPassword)) {
            flash.addFlashAttribute("error", "Новый пароль и повтор не совпадают");
            return "redirect:/account";
        }

        Flash.run(flash, "Пароль изменён. Другие сессии завершены.", () -> backend.post(
                Admin.token(),
                "/admin/me/password",
                Map.of("currentPassword", currentPassword, "newPassword", newPassword),
                Void.class));

        return "redirect:/account";
    }
}
