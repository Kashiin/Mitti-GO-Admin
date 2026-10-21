package uz.mittisoftware.mittigo.admin.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uz.mittisoftware.mittigo.admin.backend.BackendClient;
import uz.mittisoftware.mittigo.admin.web.Models.Items;
import uz.mittisoftware.mittigo.admin.web.Models.PromoCode;

/**
 * Promo codes: create one for 1 / 3 / 6 / 12 months or forever, limit activations and the last day, switch it off.
 * A code turns on Pro for the install that enters it (server `POST /promo/redeem`).
 */
@Controller
class PromoCodesController {

    private static final ParameterizedTypeReference<Items<PromoCode>> LIST = new ParameterizedTypeReference<>() {
    };

    /** A promo period: the server's id and the Russian label. */
    public record Period(String id, String label) {
    }

    private static final List<Period> PERIODS = List.of(
            new Period("M1", "1 месяц"),
            new Period("M3", "3 месяца"),
            new Period("M6", "6 месяцев"),
            new Period("M12", "12 месяцев"),
            new Period("LIFETIME", "Навсегда"));

    private final BackendClient backend;

    PromoCodesController(BackendClient backend) {
        this.backend = backend;
    }

    @GetMapping("/promo-codes")
    String list(Model model) {
        model.addAttribute("periods", PERIODS);
        model.addAttribute("periodLabels", PERIODS.stream().collect(Collectors.toMap(Period::id, Period::label)));
        List<PromoCode> codes = backend.get(Admin.token(), "/admin/promo-codes", LIST).items();

        model.addAttribute("activeCodes", codes.stream().filter(PromoCode::isWorking).toList());
        model.addAttribute("archivedCodes", codes.stream().filter(code -> !code.isWorking()).toList());

        return "promo-codes";
    }

    @PostMapping("/promo-codes")
    String create(
            @RequestParam(required = false) String code,
            @RequestParam String period,
            @RequestParam(required = false) Integer maxRedemptions,
            @RequestParam(required = false) String validUntil,
            @RequestParam(required = false) String note,
            RedirectAttributes flash
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("period", period);
        body.put("maxRedemptions", maxRedemptions);
        body.put("validUntil", validUntil);
        body.put("note", note);

        Flash.run(flash, () -> {
            PromoCode created = backend.post(Admin.token(), "/admin/promo-codes", body, PromoCode.class);
            return "Промокод " + created.code() + " создан";
        });

        return "redirect:/promo-codes";
    }

    @PostMapping("/promo-codes/{id}/active")
    String active(@PathVariable UUID id, @RequestParam boolean active, RedirectAttributes flash) {
        Flash.run(flash, () -> {
            PromoCode changed = backend.put(Admin.token(), "/admin/promo-codes/{id}", Map.of("isActive", active), PromoCode.class, id);

            return changed.isActive()
                    ? "Промокод " + changed.code() + " включён — снова активируется"
                    : "Промокод " + changed.code() + " выключен — больше не активируется";
        });

        return "redirect:/promo-codes";
    }

    @PostMapping("/promo-codes/{id}/delete")
    String delete(
            @PathVariable UUID id,
            @RequestParam(required = false) String code,
            @RequestParam(defaultValue = "false") boolean revokePro,
            RedirectAttributes flash
    ) {
        // [code] only names the deleted code in the message; the server deletes by id. [revokePro]: take back the Pro it gave (C46).
        String name = code == null ? "Промокод" : "Промокод " + code;

        Flash.run(flash, name + (revokePro ? " удалён, выданный им Pro забран" : " удалён"), () ->
                backend.delete(Admin.token(), "/admin/promo-codes/{id}?revokePro={revoke}", id, revokePro));

        return "redirect:/promo-codes";
    }
}
