package uz.mittisoftware.mittigo.admin.web;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import uz.mittisoftware.mittigo.admin.web.Models.Recommendation;

/**
 * Recommended channels per language (setup step 3 and Add Channel in the app). A channel is added by link, @handle or id;
 * the server checks it on YouTube. Changes reach the app on its next request — no release.
 */
@Controller
class RecommendationsController {

    private static final ParameterizedTypeReference<Items<Recommendation>> LIST = new ParameterizedTypeReference<>() {
    };
    private static final List<String> LANGUAGES = List.of("ru", "uz", "en");
    private static final Set<String> MOVES = Set.of("up", "down");

    private final BackendClient backend;

    RecommendationsController(BackendClient backend) {
        this.backend = backend;
    }

    @GetMapping("/recommendations")
    String list(@RequestParam(defaultValue = "ru") String language, Model model) {
        String lang = LANGUAGES.contains(language) ? language : "ru";

        model.addAttribute("language", lang);
        model.addAttribute("languages", LANGUAGES);
        model.addAttribute("items", backend.get(Admin.token(), "/admin/recommendations?language={language}", LIST, lang).items());

        return "recommendations";
    }

    @PostMapping("/recommendations")
    String add(@RequestParam String language, @RequestParam String channel, RedirectAttributes flash) {
        Flash.run(flash, "Канал добавлен", () -> backend.post(
                Admin.token(),
                "/admin/recommendations",
                Map.of("language", language, "channel", channel),
                Recommendation.class));

        return redirect(language);
    }

    @PostMapping("/recommendations/{id}/active")
    String active(@PathVariable UUID id, @RequestParam String language, @RequestParam boolean active, RedirectAttributes flash) {
        Flash.run(flash, active ? "Канал включён" : "Канал скрыт из рекомендаций", () ->
                backend.put(Admin.token(), "/admin/recommendations/{id}", Map.of("isActive", active), Recommendation.class, id));

        return redirect(language);
    }

    @PostMapping("/recommendations/{id}/move")
    String move(
            @PathVariable UUID id,
            @RequestParam String language,
            @RequestParam int position,
            @RequestParam String direction,
            RedirectAttributes flash
    ) {
        if (!MOVES.contains(direction)) return redirect(language);
        int target = Math.max(0, direction.equals("up") ? position - 1 : position + 1);

        Flash.run(flash, "Порядок изменён", () ->
                backend.put(Admin.token(), "/admin/recommendations/{id}", Map.of("position", target), Recommendation.class, id));

        return redirect(language);
    }

    @PostMapping("/recommendations/{id}/delete")
    String delete(@PathVariable UUID id, @RequestParam String language, RedirectAttributes flash) {
        Flash.run(flash, "Канал убран из рекомендаций", () -> backend.delete(Admin.token(), "/admin/recommendations/{id}", id));

        return redirect(language);
    }

    private static String redirect(String language) {
        return "redirect:/recommendations?language=" + (LANGUAGES.contains(language) ? language : "ru");
    }
}
