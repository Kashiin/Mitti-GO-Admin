package uz.mittisoftware.mittigo.admin.web;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uz.mittisoftware.mittigo.admin.backend.BackendClient;

/**
 * "Контент → Категории" (C50): the Learning topics with how many videos each language has, and inside a topic its videos —
 * search, language, paging, and hiding / showing a topic or a video (the app drops it at its next load; nothing is deleted).
 */
@Controller
class CategoriesController {

    private static final int PAGE_SIZE = 50;
    private static final Set<String> LANGUAGES = Set.of("ru", "uz", "en");
    private static final String THUMBNAIL = "https://i.ytimg.com/vi/%s/mqdefault.jpg";
    private static final String WATCH = "https://www.youtube.com/watch?v=%s";

    /** As the server sends them. */
    record TopicRow(String code, String titleRu, String titleUz, String titleEn, boolean active, long ru, long uz, long en) {
        public long total() {
            return ru + uz + en;
        }
    }

    record VideoRow(String id, String language, String youtubeId, String title, String channel, List<String> topics, String kind,
                    Integer durationSeconds, boolean active) {
    }

    record Videos(long total, List<VideoRow> items) {
    }

    /** A video as shown: [kind] "Песенка" / "Мультсерия", [duration] "4:52". */
    public record VideoView(String id, String language, String title, String channel, String kind, String duration, boolean active,
                            String thumbnail, String link) {
    }

    private final BackendClient backend;

    CategoriesController(BackendClient backend) {
        this.backend = backend;
    }

    @GetMapping("/categories")
    String topics(Model model) {
        List<TopicRow> topics = backend.get(Admin.token(), "/admin/learning/topics", new ParameterizedTypeReference<List<TopicRow>>() { });

        model.addAttribute("topics", topics);
        model.addAttribute("totalVideos", topics.stream().mapToLong(TopicRow::total).sum());

        return "categories";
    }

    @GetMapping("/categories/{code}")
    String videos(
            @PathVariable String code,
            @RequestParam(defaultValue = "") String language,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        String lang = LANGUAGES.contains(language) ? language : "";
        int current = Math.max(page, 0);
        List<TopicRow> topics = backend.get(Admin.token(), "/admin/learning/topics", new ParameterizedTypeReference<List<TopicRow>>() { });
        TopicRow topic = topics.stream().filter(t -> t.code().equals(code)).findFirst().orElse(null);
        Videos videos = backend.get(Admin.token(), "/admin/learning/videos?topic={topic}&language={language}&query={query}&limit={limit}&offset={offset}",
                Videos.class, code, lang, query.strip(), PAGE_SIZE, current * PAGE_SIZE);
        long pages = Math.max(1, (videos.total() + PAGE_SIZE - 1) / PAGE_SIZE);

        model.addAttribute("topic", topic);
        model.addAttribute("code", code);
        model.addAttribute("language", lang);
        model.addAttribute("query", query.strip());
        model.addAttribute("total", videos.total());
        model.addAttribute("page", current);
        model.addAttribute("pages", pages);
        model.addAttribute("hasPrevious", current > 0);
        model.addAttribute("hasNext", current + 1 < pages);
        model.addAttribute("videos", videos.items().stream().map(CategoriesController::view).toList());

        return "category";
    }

    @PostMapping("/categories/{code}/active")
    String topicActive(@PathVariable String code, @RequestParam boolean active, RedirectAttributes flash) {
        Flash.run(flash, active ? "Категория снова видна в приложении" : "Категория скрыта в приложении",
                () -> backend.post(Admin.token(), "/admin/learning/topics/{code}/active", Map.of("active", active), Map.class, code));

        return "redirect:/categories";
    }

    @PostMapping("/categories/{code}/videos/{id}/active")
    String videoActive(
            @PathVariable String code,
            @PathVariable String id,
            @RequestParam boolean active,
            @RequestParam(defaultValue = "") String language,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            RedirectAttributes flash
    ) {
        Flash.run(flash, active ? "Видео снова видно в приложении" : "Видео скрыто в приложении",
                () -> backend.post(Admin.token(), "/admin/learning/videos/{id}/active", Map.of("active", active), Map.class, id));
        flash.addAttribute("language", language);
        flash.addAttribute("query", query);
        flash.addAttribute("page", page);

        return "redirect:/categories/" + code;
    }

    private static VideoView view(VideoRow video) {
        String kind = "SONG".equals(video.kind()) ? "Песенка" : "EPISODE".equals(video.kind()) ? "Мультсерия" : null;

        return new VideoView(video.id(), video.language().toUpperCase(), video.title(), video.channel(), kind, duration(video.durationSeconds()),
                video.active(), THUMBNAIL.formatted(video.youtubeId()), WATCH.formatted(video.youtubeId()));
    }

    private static String duration(Integer seconds) {
        if (seconds == null) return null;
        int h = seconds / 3600;
        int m = seconds % 3600 / 60;
        int s = seconds % 60;

        return h > 0 ? "%d:%02d:%02d".formatted(h, m, s) : "%d:%02d".formatted(m, s);
    }
}
