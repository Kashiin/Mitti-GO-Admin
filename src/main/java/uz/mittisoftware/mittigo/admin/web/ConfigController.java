package uz.mittisoftware.mittigo.admin.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
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
import uz.mittisoftware.mittigo.admin.web.Models.Contact;
import uz.mittisoftware.mittigo.admin.web.Models.Items;
import uz.mittisoftware.mittigo.admin.web.Models.LegalLink;
import uz.mittisoftware.mittigo.admin.web.Models.Plan;
import uz.mittisoftware.mittigo.admin.web.Models.ReleaseNote;
import uz.mittisoftware.mittigo.admin.web.Models.Setting;

/**
 * What the app reads without a release: support contacts, What's New, legal links, Pro prices and app settings. An empty
 * contact or link is simply hidden in the app (BL §16).
 */
@Controller
class ConfigController {

    private static final ParameterizedTypeReference<Items<Contact>> CONTACTS = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<Items<ReleaseNote>> NOTES = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<Items<LegalLink>> LEGAL = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<Items<Plan>> PLANS = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<Items<Setting>> SETTINGS = new ParameterizedTypeReference<>() {
    };

    /** Contact types in the app's order, with a Russian label and a hint for the link. */
    public record ContactType(String type, String label, String uriHint) {
    }

    private static final List<ContactType> CONTACT_TYPES = List.of(
            new ContactType("EMAIL", "Почта", "mailto:help@…"),
            new ContactType("PHONE", "Телефон", "tel:+998…"),
            new ContactType("TELEGRAM", "Telegram", "https://t.me/…"),
            new ContactType("INSTAGRAM", "Instagram", "https://instagram.com/…"),
            new ContactType("WEBSITE", "Сайт", "https://…"),
            new ContactType("HOURS", "Часы работы", null));

    public record LegalKey(String key, String label) {
    }

    private static final List<LegalKey> LEGAL_KEYS = List.of(
            new LegalKey("PRIVACY_POLICY", "Политика конфиденциальности"),
            new LegalKey("TERMS", "Условия использования"));

    private final BackendClient backend;

    ConfigController(BackendClient backend) {
        this.backend = backend;
    }

    // --- Contacts --------------------------------------------------------------------------------------------------------

    @GetMapping("/contacts")
    String contacts(Model model) {
        Map<String, Contact> saved = backend.get(Admin.token(), "/admin/contacts", CONTACTS).items().stream()
                .collect(Collectors.toMap(Contact::type, Function.identity()));

        model.addAttribute("types", CONTACT_TYPES);
        model.addAttribute("saved", saved);

        return "contacts";
    }

    @PostMapping("/contacts/{type}")
    String saveContact(@PathVariable String type, @RequestParam String value, @RequestParam(required = false) String uri, RedirectAttributes flash) {
        if (value.isBlank()) {
            Flash.run(flash, "Контакт убран — в приложении его не будет", () -> backend.delete(Admin.token(), "/admin/contacts/{type}", type));
        } else {
            Map<String, Object> body = new HashMap<>();
            body.put("value", value);
            body.put("uri", uri == null || uri.isBlank() ? null : uri.strip());
            Flash.run(flash, "Контакт сохранён", () -> backend.put(Admin.token(), "/admin/contacts/{type}", body, Contact.class, type));
        }

        return "redirect:/contacts";
    }

    // --- What's New ------------------------------------------------------------------------------------------------------

    @GetMapping("/release-notes")
    String releaseNotes(Model model) {
        model.addAttribute("notes", backend.get(Admin.token(), "/admin/release-notes", NOTES).items());

        return "release-notes";
    }

    @PostMapping("/release-notes")
    String addReleaseNote(
            @RequestParam String version,
            @RequestParam String releasedOn,
            @RequestParam String textUz,
            @RequestParam String textRu,
            @RequestParam String textEn,
            RedirectAttributes flash
    ) {
        Map<String, Object> body = Map.of("version", version.strip(), "releasedOn", releasedOn, "textUz", textUz, "textRu", textRu, "textEn", textEn);

        Flash.run(flash, "Версия " + version.strip() + " добавлена", () -> backend.post(Admin.token(), "/admin/release-notes", body, ReleaseNote.class));

        return "redirect:/release-notes";
    }

    @PostMapping("/release-notes/{id}/delete")
    String deleteReleaseNote(@PathVariable UUID id, RedirectAttributes flash) {
        Flash.run(flash, "Запись удалена", () -> backend.delete(Admin.token(), "/admin/release-notes/{id}", id));

        return "redirect:/release-notes";
    }

    // --- Legal links -----------------------------------------------------------------------------------------------------

    @GetMapping("/legal")
    String legal(Model model) {
        Map<String, String> saved = backend.get(Admin.token(), "/admin/legal-links", LEGAL).items().stream()
                .collect(Collectors.toMap(LegalLink::key, LegalLink::url));

        model.addAttribute("keys", LEGAL_KEYS);
        model.addAttribute("saved", saved);

        return "legal";
    }

    @PostMapping("/legal/{key}")
    String saveLegal(@PathVariable String key, @RequestParam String url, RedirectAttributes flash) {
        if (url.isBlank()) {
            Flash.run(flash, "Ссылка убрана — в приложении её не будет", () -> backend.delete(Admin.token(), "/admin/legal-links/{key}", key));
        } else {
            Flash.run(flash, "Ссылка сохранена", () -> backend.put(Admin.token(), "/admin/legal-links/{key}", Map.of("url", url.strip()), LegalLink.class, key));
        }

        return "redirect:/legal";
    }

    // --- Pro prices ------------------------------------------------------------------------------------------------------

    @GetMapping("/prices")
    String prices(Model model) {
        model.addAttribute("plans", backend.get(Admin.token(), "/admin/plans", PLANS).items());
        model.addAttribute("limits", backend.get(Admin.token(), "/admin/pro-limits", ProLimits.class));

        return "prices";
    }

    @PostMapping("/prices/{months}")
    String savePrice(
            @PathVariable int months,
            @RequestParam String priceUsd,
            @RequestParam(defaultValue = "false") boolean active,
            @RequestParam(required = false) String storeProductId,
            RedirectAttributes flash
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("priceUsd", priceUsd.strip().replace(',', '.'));
        body.put("isActive", active);
        body.put("storeProductId", storeProductId);

        Flash.run(flash, "Цена сохранена", () -> backend.put(Admin.token(), "/admin/plans/{months}", body, Plan.class, months));

        return "redirect:/prices";
    }

    /** What Pro adds (C45): [maxDevices] counts the parent phone. Free is fixed: 5 channels, 10 playlists (in the app), 3 devices (`device_limit.free_max`, C51). */
    public record ProLimits(int maxChannels, int maxPlaylists, int maxDevices) {
    }

    @PostMapping("/prices/limits")
    String saveLimits(
            @RequestParam int maxChannels,
            @RequestParam int maxPlaylists,
            @RequestParam int maxDevices,
            RedirectAttributes flash
    ) {
        ProLimits body = new ProLimits(maxChannels, maxPlaylists, maxDevices);

        Flash.run(flash, "Лимиты Pro сохранены — в приложении обновятся при следующем открытии", () -> backend.put(Admin.token(), "/admin/pro-limits", body, ProLimits.class));

        return "redirect:/prices";
    }

    // --- App settings ----------------------------------------------------------------------------------------------------

    @GetMapping("/settings")
    String settings(Model model) {
        model.addAttribute("settings", backend.get(Admin.token(), "/admin/settings", SETTINGS).items());

        return "settings";
    }

    @PostMapping("/settings")
    String saveSetting(@RequestParam String key, @RequestParam String value, RedirectAttributes flash) {
        String name = key.strip().toUpperCase();

        Flash.run(flash, "Настройка " + name + " сохранена", () ->
                backend.put(Admin.token(), "/admin/settings/{key}", Map.of("value", value), Setting.class, name));

        return "redirect:/settings";
    }

    @PostMapping("/settings/{key}/delete")
    String deleteSetting(@PathVariable String key, RedirectAttributes flash) {
        Flash.run(flash, "Настройка удалена", () -> backend.delete(Admin.token(), "/admin/settings/{key}", key));

        return "redirect:/settings";
    }
}
