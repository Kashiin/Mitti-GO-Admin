package uz.mittisoftware.mittigo.admin.backend;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** An error answer of the server: `{ "error": { "code", "message", "fields"? } }`, or no answer at all ([status] 0). */
public class BackendException extends RuntimeException {

    private static final Map<String, String> FIELD_NAMES = Map.ofEntries(
            Map.entry("language", "Язык"),
            Map.entry("channel", "Канал"),
            Map.entry("position", "Позиция"),
            Map.entry("value", "Значение"),
            Map.entry("uri", "Ссылка"),
            Map.entry("version", "Версия"),
            Map.entry("releasedOn", "Дата"),
            Map.entry("textUz", "Текст (uz)"),
            Map.entry("textRu", "Текст (ru)"),
            Map.entry("textEn", "Текст (en)"),
            Map.entry("url", "Адрес"),
            Map.entry("priceUsd", "Цена"),
            Map.entry("storeProductId", "ID товара в магазине"),
            Map.entry("key", "Ключ"),
            Map.entry("code", "Код"),
            Map.entry("period", "Срок"),
            Map.entry("maxRedemptions", "Лимит активаций"),
            Map.entry("validUntil", "Действует до"),
            Map.entry("note", "Заметка"),
            Map.entry("currentPassword", "Текущий пароль"),
            Map.entry("newPassword", "Новый пароль"),
            Map.entry("login", "Логин"),
            Map.entry("password", "Пароль"));

    private final int status;
    private final String code;
    private final List<Map<String, String>> fields;
    private final Long retryAfterSeconds;

    public BackendException(int status, String code, String message, List<Map<String, String>> fields, Long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.code = code;
        this.fields = fields == null ? List.of() : fields;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int status() {
        return status;
    }

    public boolean isUnauthorized() {
        return status == 401;
    }

    /** A line for the owner, in Russian. */
    public String userMessage() {
        if (!fields.isEmpty()) {
            return fields.stream()
                    .map(f -> FIELD_NAMES.getOrDefault(f.get("field"), f.get("field")) + ": " + f.get("message"))
                    .collect(Collectors.joining("; "));
        }

        return switch (code == null ? "" : code) {
            case "UNAUTHORIZED" -> "Неверный логин или пароль";
            case "TOO_MANY_REQUESTS" -> retryAfterSeconds == null
                    ? "Слишком много попыток, подождите"
                    : "Вход временно заблокирован, попробуйте через " + Math.max(1, retryAfterSeconds / 60) + " мин";
            case "NOT_FOUND" -> "Не найдено (канала с такой ссылкой нет на YouTube?)";
            case "FORBIDDEN" -> "Это может только главный администратор";
            case "CONFLICT" -> "Такая запись уже есть" + (getMessage() != null && getMessage().contains("login") ? " — этот логин занят" : "");
            case "QUOTA_EXCEEDED" -> "Квота YouTube на сегодня закончилась";
            case "BAD_REQUEST" -> "Неверные данные: " + getMessage();
            case "NO_CONNECTION" -> "Сервер Mitti GO не отвечает — он запущен?";
            default -> "Ошибка сервера (" + status + ")";
        };
    }
}
