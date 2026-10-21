package uz.mittisoftware.mittigo.admin.web;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uz.mittisoftware.mittigo.admin.backend.BackendClient;
import uz.mittisoftware.mittigo.admin.backend.BackendException;
import uz.mittisoftware.mittigo.admin.web.Models.ImportResult;
import uz.mittisoftware.mittigo.admin.web.Models.Items;
import uz.mittisoftware.mittigo.admin.web.Models.OfficialChannel;

/**
 * "Каналы": the owner's official channels per language (ENG · RU · UZB). Filled by importing an Excel / CSV / JSON file —
 * the server checks each row on YouTube and skips duplicates — and downloadable in the same three formats.
 */
@Controller
class ChannelsController {

    private static final ParameterizedTypeReference<Items<OfficialChannel>> LIST = new ParameterizedTypeReference<>() {
    };
    private static final List<String> LANGUAGES = List.of("en", "ru", "uz");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final BackendClient backend;
    private final ChannelFiles files;

    ChannelsController(BackendClient backend, ChannelFiles files) {
        this.backend = backend;
        this.files = files;
    }

    @GetMapping("/channels")
    String list(@RequestParam(defaultValue = "en") String language, Model model) {
        String lang = language(language);

        model.addAttribute("language", lang);
        model.addAttribute("languages", LANGUAGES);
        model.addAttribute("items", backend.get(Admin.token(), "/admin/official-channels?language={language}", LIST, lang).items());

        return "channels";
    }

    /** A row that wasn't added, with the language it was meant for. */
    public record ProblemView(String language, int row, String input, String reason) {
    }

    /**
     * Imports a file into the open tab — or, for a workbook with sheets named after languages, each sheet into its own
     * language. One summary line per language; rows that weren't added are listed below it.
     */
    @PostMapping("/channels/import")
    String importFile(@RequestParam String language, @RequestParam("file") MultipartFile file, RedirectAttributes flash) {
        String lang = language(language);
        Map<String, List<String>> byLanguage;

        try {
            byLanguage = files.read(file.getOriginalFilename(), file.getInputStream());
        } catch (ChannelFiles.UnreadableFile | IOException e) {
            flash.addFlashAttribute("error", e instanceof ChannelFiles.UnreadableFile ? e.getMessage() : "Не удалось прочитать файл");
            return redirect(lang);
        }

        if (byLanguage.values().stream().flatMap(List::stream).allMatch(String::isBlank)) {
            flash.addFlashAttribute("error", "В файле не нашлось ссылок на каналы, @handle или ID канала (UC…)");
            return redirect(lang);
        }

        List<String> summary = new ArrayList<>();
        List<ProblemView> problems = new ArrayList<>();

        for (Map.Entry<String, List<String>> sheet : byLanguage.entrySet()) {
            String target = sheet.getKey().equals(ChannelFiles.CURRENT_LANGUAGE) ? lang : sheet.getKey();

            if (sheet.getValue().isEmpty()) continue;
            try {
                ImportResult result = backend.postLong(Admin.token(), "/admin/official-channels/import",
                        Map.of("language", target, "channels", sheet.getValue()), ImportResult.class);

                summary.add(title(target) + ": добавлено " + result.added() + ", дубликатов " + result.duplicates()
                        + (result.problems().isEmpty() ? "" : ", не добавлено " + result.problems().size()));
                result.problems().forEach(p -> problems.add(new ProblemView(title(target), p.row(), p.input(), p.reason())));
            } catch (BackendException e) {
                if (e.isUnauthorized()) throw e;
                summary.add(title(target) + ": " + e.userMessage());
            }
        }

        flash.addFlashAttribute("success", "Импорт — " + String.join(" · ", summary));
        flash.addFlashAttribute("problems", problems);

        return redirect(lang);
    }

    private static String title(String language) {
        return switch (language) {
            case "ru" -> "RU";
            case "uz" -> "UZB";
            default -> "ENG";
        };
    }

    @GetMapping("/channels/export")
    ResponseEntity<byte[]> export(@RequestParam String language, @RequestParam String format) {
        String lang = language(language);
        ChannelFiles.Format type = ChannelFiles.Format.of(format);
        List<ChannelFiles.Line> lines = backend.get(Admin.token(), "/admin/official-channels?language={language}", LIST, lang).items().stream()
                .map(item -> new ChannelFiles.Line(
                        item.channelId(),
                        item.channel() == null ? null : item.channel().title(),
                        item.channel() == null ? null : item.channel().handle(),
                        item.addedAt() == null ? null : DATE.format(item.addedAt())))
                .toList();

        return download(files.write(type, lang, lines), "mitti-channels-" + lang + "." + type.extension, type);
    }

    @GetMapping("/channels/template")
    ResponseEntity<byte[]> template(@RequestParam String format) {
        ChannelFiles.Format type = ChannelFiles.Format.of(format);

        return download(files.template(type), "mitti-channels-template." + type.extension, type);
    }

    @PostMapping("/channels/{id}/delete")
    String delete(@PathVariable UUID id, @RequestParam String language, RedirectAttributes flash) {
        Flash.run(flash, "Канал убран из списка", () -> backend.delete(Admin.token(), "/admin/official-channels/{id}", id));

        return redirect(language(language));
    }

    private static ResponseEntity<byte[]> download(byte[] body, String fileName, ChannelFiles.Format type) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, type.contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(body);
    }

    private static String language(String language) {
        return LANGUAGES.contains(language) ? language : "en";
    }

    private static String redirect(String language) {
        return "redirect:/channels?language=" + language;
    }
}
