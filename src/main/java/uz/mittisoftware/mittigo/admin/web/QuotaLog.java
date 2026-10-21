package uz.mittisoftware.mittigo.admin.web;

import java.util.List;
import java.util.Map;
import uz.mittisoftware.mittigo.admin.web.Models.Quota;

/**
 * Where today's YouTube quota went, in Russian: the server logs each call with our endpoint's route pattern and the
 * YouTube method; this names them for people ("Лента: видео канала", "videos.list — подробности видео").
 */
final class QuotaLog {

    private static final String YOUTUBE = "/api/v1/youtube";
    private static final String ADMIN = "/api/v1/admin";

    private static final Map<String, String> SOURCES = Map.ofEntries(
            Map.entry(YOUTUBE + "/resolve", "Приложение: поиск по ссылке / @handle"),
            Map.entry(YOUTUBE + "/search", "Приложение: поиск по названию"),
            Map.entry(YOUTUBE + "/channels/{channelId}", "Приложение: карточка канала"),
            Map.entry(YOUTUBE + "/channels/{channelId}/preview", "Приложение: превью канала перед добавлением"),
            Map.entry(YOUTUBE + "/channels/{channelId}/videos", "Приложение: лента — видео канала"),
            Map.entry(YOUTUBE + "/channels/{channelId}/playlists", "Приложение: плейлисты канала"),
            Map.entry(YOUTUBE + "/playlists/{playlistId}", "Приложение: карточка плейлиста"),
            Map.entry(YOUTUBE + "/playlists/{playlistId}/items", "Приложение: видео плейлиста"),
            Map.entry(YOUTUBE + "/videos", "Приложение: подробности видео"),
            Map.entry(YOUTUBE + "/channels", "Приложение: названия и аватары каналов"),
            Map.entry(YOUTUBE + "/playlists", "Приложение: названия плейлистов"),
            Map.entry("/api/v1/config/recommendations", "Приложение: рекомендованные каналы"),
            Map.entry("background", "Сервер сам, без запроса"));

    private static final Map<String, String> METHODS = Map.of(
            "search.list", "поиск — 100 ед.",
            "channels.list", "данные каналов",
            "playlists.list", "данные плейлистов",
            "playlistItems.list", "список видео",
            "videos.list", "подробности видео");

    /** A group of calls as shown. */
    public record UsageLine(String source, String method, int calls, int units) {
    }

    /** One call as shown, in Tashkent time. */
    public record CallLine(String time, String source, String method, int units) {
    }

    private QuotaLog() {
    }

    static List<UsageLine> usage(Quota quota) {
        return quota.usage().stream()
                .map(u -> new UsageLine(source(u.source()), method(u.method()), u.calls(), u.units()))
                .toList();
    }

    static List<CallLine> recent(Quota quota) {
        return quota.recent().stream()
                .map(c -> new CallLine(AuditLog.time(c.at()), source(c.source()), method(c.method()), c.units()))
                .toList();
    }

    private static String source(String pattern) {
        String known = SOURCES.get(pattern);

        if (known != null) return known;
        if (pattern.startsWith(ADMIN + "/recommendations")) return "Админка: рекомендации";
        if (pattern.startsWith(ADMIN + "/official-channels")) return "Админка: каналы и импорт";
        if (pattern.startsWith(ADMIN)) return "Админка";

        return pattern;
    }

    private static String method(String method) {
        String meaning = METHODS.get(method);

        return meaning == null ? method : method + " · " + meaning;
    }
}
