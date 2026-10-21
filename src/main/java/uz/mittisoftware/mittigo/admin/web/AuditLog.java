package uz.mittisoftware.mittigo.admin.web;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import uz.mittisoftware.mittigo.admin.web.Models.AuditEntry;

/**
 * Turns the server's change log (action codes, ids, English details) into Russian lines for people: "Рекомендация
 * перемещена · RU · Фиксики · место 12 → 5", in Tashkent time. An unknown code is shown as it is.
 */
final class AuditLog {

    // The owner's team works in Uzbekistan; the server stores UTC.
    private static final ZoneId ZONE = ZoneId.of("Asia/Tashkent");
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern CHANNEL_ID = Pattern.compile("UC[A-Za-z0-9_-]{22}");
    private static final Pattern POSITION = Pattern.compile("position (\\d+) → (\\d+)");
    private static final Pattern IMPORT = Pattern.compile("(\\w+): \\+(\\d+), (\\d+) duplicates, (\\d+) problems");
    private static final Pattern PRICE = Pattern.compile("(\\d+) mo = \\$([\\d.]+)( \\(off\\))?");
    private static final Pattern PROMO = Pattern.compile("…(\\S+)(.*)");

    /** Filter tabs of the log page: the action-code prefix → its name. "" = everything. */
    static final Map<String, String> GROUPS = new LinkedHashMap<>();

    static {
        GROUPS.put("", "Все");
        GROUPS.put("RECOMMENDATION_", "Рекомендации");
        GROUPS.put("OFFICIAL_CHANNEL", "Каналы");
        GROUPS.put("PROMO_", "Промокоды");
        GROUPS.put("PRICE_", "Цены Pro");
        GROUPS.put("CONTACT_", "Контакты");
        GROUPS.put("RELEASE_NOTE_", "Что нового");
        GROUPS.put("LEGAL_LINK_", "Юридические ссылки");
        GROUPS.put("SETTING_", "Настройки");
        GROUPS.put("LOGIN", "Входы");
        GROUPS.put("ADMIN_", "Администраторы");
        GROUPS.put("PASSWORD_", "Пароль");
    }

    private static final Map<String, String> ACTIONS = Map.ofEntries(
            Map.entry("LOGIN", "Вход в админку"),
            Map.entry("LOGIN_LOCKED", "Вход заблокирован: много неверных паролей"),
            Map.entry("PASSWORD_CHANGED", "Пароль изменён"),
            Map.entry("ADMIN_CREATED", "Администратор создан"),
            Map.entry("ADMIN_DISABLED", "Администратор отключён"),
            Map.entry("ADMIN_ENABLED", "Администратор снова включён"),
            Map.entry("ADMIN_DELETED", "Администратор удалён"),
            Map.entry("RECOMMENDATION_ADDED", "Рекомендация добавлена"),
            Map.entry("RECOMMENDATION_CHANGED", "Рекомендация изменена"),
            Map.entry("RECOMMENDATION_REMOVED", "Рекомендация удалена"),
            Map.entry("OFFICIAL_CHANNELS_IMPORTED", "Импорт каналов"),
            Map.entry("OFFICIAL_CHANNEL_REMOVED", "Канал убран из списка"),
            Map.entry("PROMO_CREATED", "Промокод создан"),
            Map.entry("PROMO_CHANGED", "Промокод изменён"),
            Map.entry("PROMO_DELETED", "Промокод удалён"),
            Map.entry("PRICE_SET", "Цена Pro изменена"),
            Map.entry("CONTACT_SET", "Контакт сохранён"),
            Map.entry("CONTACT_REMOVED", "Контакт удалён"),
            Map.entry("RELEASE_NOTE_ADDED", "«Что нового»: запись добавлена"),
            Map.entry("RELEASE_NOTE_CHANGED", "«Что нового»: запись изменена"),
            Map.entry("RELEASE_NOTE_REMOVED", "«Что нового»: запись удалена"),
            Map.entry("LEGAL_LINK_SET", "Юридическая ссылка сохранена"),
            Map.entry("LEGAL_LINK_REMOVED", "Юридическая ссылка удалена"),
            Map.entry("SETTING_SET", "Настройка изменена"),
            Map.entry("SETTING_REMOVED", "Настройка удалена"));

    private static final Map<String, String> PERIODS = Map.of(
            "M1", "1 месяц", "M3", "3 месяца", "M6", "6 месяцев", "M12", "12 месяцев", "LIFETIME", "навсегда");

    /** One log line as shown. */
    public record Line(String when, String whenShort, String admin, String what, String details) {
    }

    private AuditLog() {
    }

    /** [time] in Tashkent time, `HH:mm:ss`; null stays null. */
    static String time(OffsetDateTime time) {
        return time == null ? null : CLOCK.format(time.atZoneSameInstant(ZONE));
    }

    /** [time] in Tashkent time, `dd.MM.yyyy HH:mm`; null stays null. */
    static String when(OffsetDateTime time) {
        return time == null ? null : FULL.format(time.atZoneSameInstant(ZONE));
    }

    static List<Line> lines(List<AuditEntry> entries) {
        return entries.stream().map(AuditLog::line).toList();
    }

    private static Line line(AuditEntry entry) {
        var local = entry.createdAt() == null ? null : entry.createdAt().atZoneSameInstant(ZONE);

        return new Line(
                local == null ? "" : FULL.format(local),
                local == null ? "" : SHORT.format(local),
                entry.admin() == null ? "удалённый админ" : entry.admin(),
                ACTIONS.getOrDefault(entry.action(), entry.action()),
                details(entry));
    }

    private static String details(AuditEntry entry) {
        String raw = entry.details() == null ? "" : entry.details();

        return switch (entry.action()) {
            case "RECOMMENDATION_ADDED", "RECOMMENDATION_CHANGED", "RECOMMENDATION_REMOVED" -> recommendation(raw, entry.channelTitle());
            case "OFFICIAL_CHANNEL_REMOVED" -> channel(raw, entry.channelTitle());
            case "OFFICIAL_CHANNELS_IMPORTED" -> imported(raw);
            case "PRICE_SET" -> price(raw);
            case "PROMO_CREATED", "PROMO_CHANGED", "PROMO_DELETED" -> promo(raw);
            case "RELEASE_NOTE_ADDED", "RELEASE_NOTE_CHANGED" -> "версия " + raw;
            default -> raw;
        };
    }

    /** "ru UC… position 12 → 5 hidden" → "RU · Фиксики · место 12 → 5 · скрыта". */
    private static String recommendation(String raw, String title) {
        String[] parts = raw.split(" ", 2);
        String rest = parts.length > 1 ? parts[1] : "";
        StringBuilder out = new StringBuilder(language(parts[0])).append(" · ").append(channel(rest, title));
        Matcher position = POSITION.matcher(rest);

        if (position.find()) out.append(" · место ").append(position.group(1)).append(" → ").append(position.group(2));
        if (rest.endsWith(" hidden")) out.append(" · скрыта");
        if (rest.endsWith(" shown")) out.append(" · показывается");

        return out.toString();
    }

    /** The channel's name when the server knows it, otherwise its id. */
    private static String channel(String raw, String title) {
        if (title != null && !title.isBlank()) return title;

        Matcher id = CHANNEL_ID.matcher(raw);

        return id.find() ? id.group() : raw;
    }

    /** "en: +93, 7 duplicates, 0 problems" → "ENG · добавлено 93, дубликатов 7, не добавлено 0". */
    private static String imported(String raw) {
        Matcher m = IMPORT.matcher(raw);

        return m.matches()
                ? language(m.group(1)) + " · добавлено " + m.group(2) + ", дубликатов " + m.group(3) + ", не добавлено " + m.group(4)
                : raw;
    }

    /** "12 mo = $28.99 (off)" → "12 мес. — $28.99 · выключена". */
    private static String price(String raw) {
        Matcher m = PRICE.matcher(raw);

        return m.matches() ? m.group(1) + " мес. — $" + m.group(2) + (m.group(3) == null ? "" : " · выключена") : raw;
    }

    /** "…ABCD M3" / "…ABCD on" / "…ABCD (used 2)" → "…ABCD · 3 месяца" / "· включён" / "· использован 2 раза". */
    private static String promo(String raw) {
        Matcher m = PROMO.matcher(raw);

        if (!m.matches()) return raw;
        String rest = m.group(2).strip();
        String tail = switch (rest) {
            case "" -> "";
            case "on" -> "включён";
            case "off" -> "выключен";
            default -> rest.startsWith("(used ") ? "использован: " + rest.replaceAll("\\D", "") : PERIODS.getOrDefault(rest, rest);
        };

        return "…" + m.group(1) + (tail.isEmpty() ? "" : " · " + tail);
    }

    private static String language(String code) {
        return switch (code) {
            case "en" -> "ENG";
            case "ru" -> "RU";
            case "uz" -> "UZB";
            default -> code;
        };
    }
}
