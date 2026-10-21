package uz.mittisoftware.mittigo.admin.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads and writes channel lists for the "Каналы" page: Excel (.xlsx / .xls), CSV and JSON. On import, each row gives one
 * channel — a link, an @handle or a `UC…` id — taken from the column named `channel` (or url / link / handle / channel_id),
 * else from the first column. The server checks every row against YouTube and skips duplicates.
 */
@Component
class ChannelFiles {

    enum Format {
        XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        CSV("csv", "text/csv; charset=UTF-8"),
        JSON("json", "application/json");

        final String extension;
        final String contentType;

        Format(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        static Format of(String value) {
            for (Format format : values()) if (format.extension.equalsIgnoreCase(value)) return format;

            throw new IllegalArgumentException("Unknown format");
        }
    }

    /** One exported row. */
    record Line(String channelId, String title, String handle, String addedAt) {
    }

    /** The import file couldn't be read at all (wrong type, broken file). */
    static final class UnreadableFile extends RuntimeException {
        UnreadableFile(String message) {
            super(message);
        }
    }

    // The import tab's own language, for files that don't say.
    static final String CURRENT_LANGUAGE = "";
    // A YouTube link, an @handle or a channel id — what a channel cell looks like.
    private static final Pattern CHANNEL_LIKE =
            Pattern.compile("(youtube\\.com/|youtu\\.be/|^@[A-Za-z0-9._-]{3,}$|^UC[A-Za-z0-9_-]{22}$)", Pattern.CASE_INSENSITIVE);
    private static final Set<String> COLUMN_NAMES = Set.of("channel", "url", "link", "handle", "channel_id", "channelid", "канал", "ссылка", "kanal");
    private static final String[] HEADER = {"channel", "title", "handle", "url", "added_at"};
    private static final String[] EXPORT_HEADER = {"№", "Название канала", "Ссылка на YouTube", "@handle", "Добавлен"};
    private static final String[] TEMPLATE_HEADER = {
        "№", "Название канала", "Ссылка на YouTube", "Подписчики ≈", "Формат", "Примечание", "Источник официальности", "Основание",
    };
    // Sheet names the import recognises as languages (see sheetLanguage).
    private static final Map<String, String> SHEET_NAMES = new LinkedHashMap<>();

    static {
        SHEET_NAMES.put("en", "Английский");
        SHEET_NAMES.put("ru", "Русский");
        SHEET_NAMES.put("uz", "Узбекский");
    }
    private static final List<String> TEMPLATE = List.of(
            "https://www.youtube.com/@channelhandle",
            "@anotherchannel",
            "UCxxxxxxxxxxxxxxxxxxxxxx");

    private final JsonMapper json;

    ChannelFiles(JsonMapper json) {
        this.json = json;
    }

    // --- Import ----------------------------------------------------------------------------------------------------------

    /**
     * The channels of a file, by language: a workbook whose sheets are named after languages (English / Английский / EN, …)
     * gives each sheet to its language; anything else gives one list under [CURRENT_LANGUAGE] — the tab it was imported in.
     */
    Map<String, List<String>> read(String fileName, InputStream in) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);

        try {
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) return readExcel(in);
            if (name.endsWith(".csv")) return Map.of(CURRENT_LANGUAGE, readCsv(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            if (name.endsWith(".json")) return Map.of(CURRENT_LANGUAGE, readJson(in));
        } catch (IOException | RuntimeException e) {
            throw new UnreadableFile("Не удалось прочитать файл — проверьте, что он не повреждён");
        }

        throw new UnreadableFile("Нужен файл .xlsx, .xls, .csv или .json");
    }

    private Map<String, List<String>> readExcel(InputStream in) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Map<String, List<String>> byLanguage = new LinkedHashMap<>();

            for (Sheet sheet : workbook) {
                String language = sheetLanguage(sheet.getSheetName());

                if (language != null) byLanguage.merge(language, pickColumn(sheetRows(sheet)), (a, b) -> {
                    List<String> all = new ArrayList<>(a);
                    all.addAll(b);
                    return all;
                });
            }

            return byLanguage.isEmpty() ? Map.of(CURRENT_LANGUAGE, pickColumn(sheetRows(workbook.getSheetAt(0)))) : byLanguage;
        }
    }

    // A cell's text, or the address of a YouTube link put on it.
    private static List<List<String>> sheetRows(Sheet sheet) {
        DataFormatter formatter = new DataFormatter();
        List<List<String>> rows = new ArrayList<>();

        for (Row row : sheet) {
            List<String> cells = new ArrayList<>();
            for (int i = 0; i < Math.max(row.getLastCellNum(), 0); i++) {
                Cell cell = row.getCell(i);
                String text = cell == null ? "" : formatter.formatCellValue(cell).strip();
                String link = cell == null || cell.getHyperlink() == null ? null : cell.getHyperlink().getAddress();

                cells.add(link != null && isChannelLike(link) && !isChannelLike(text) ? link.strip() : text);
            }
            rows.add(cells);
        }

        return rows;
    }

    /** "English", "Английский", "EN", "Русский", "Uzbek", "O‘zbekcha", "Узбекский"… → en / ru / uz; anything else → null. */
    private static String sheetLanguage(String sheetName) {
        String name = sheetName.toLowerCase(Locale.ROOT).strip();

        if (name.equals("en") || name.startsWith("eng") || name.startsWith("англ")) return "en";
        if (name.equals("ru") || name.startsWith("rus") || name.startsWith("рус")) return "ru";
        if (name.equals("uz") || name.startsWith("uzb") || name.startsWith("o‘zb") || name.startsWith("o'zb") || name.startsWith("узб")) return "uz";

        return null;
    }

    private List<String> readCsv(String text) {
        String body = text.startsWith("﻿") ? text.substring(1) : text;
        List<List<String>> rows = new ArrayList<>();

        for (String line : body.split("\\r?\\n")) {
            if (!line.isBlank()) rows.add(splitCsvLine(line, line.contains(";") && !line.contains(",") ? ';' : ','));
        }

        return pickColumn(rows);
    }

    // A small RFC 4180 reader: quoted fields may hold the separator and "" for a quote.
    private static List<String> splitCsvLine(String line, char separator) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == separator) {
                cells.add(cell.toString().strip());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString().strip());

        return cells;
    }

    /** `["…", "…"]`, `[{"channel": "…"}, …]` or `{"channels": [ … ]}`. */
    private List<String> readJson(InputStream in) throws IOException {
        JsonNode root = json.readTree(in);
        JsonNode list = root.isObject() && root.has("channels") ? root.get("channels") : root;
        List<String> channels = new ArrayList<>();

        if (!list.isArray()) throw new UnreadableFile("В JSON нужен список каналов");
        for (JsonNode item : list) {
            if (item.isTextual()) {
                channels.add(item.asText().strip());
            } else if (item.isObject()) {
                for (String field : List.of("channel", "url", "link", "handle", "channelId", "channel_id")) {
                    if (item.has(field) && item.get(field).isTextual()) {
                        channels.add(item.get(field).asText().strip());
                        break;
                    }
                }
            }
        }

        return channels;
    }

    /**
     * The column that holds the channels, wherever the table starts: the one with the most YouTube links, @handles or `UC…`
     * ids. Only such cells are taken — titles, notes, numbers and headers around them are skipped. A file with none of them
     * falls back to the column named `channel` (or the first column), so the server can report what was wrong.
     */
    private static List<String> pickColumn(List<List<String>> rows) {
        if (rows.isEmpty()) return List.of();
        Map<Integer, Integer> hits = new LinkedHashMap<>();

        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) if (isChannelLike(row.get(i))) hits.merge(i, 1, Integer::sum);
        }

        if (!hits.isEmpty()) {
            int column = hits.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();

            return rows.stream().filter(row -> column < row.size() && isChannelLike(row.get(column))).map(row -> row.get(column)).toList();
        }

        List<String> first = rows.getFirst();
        int column = 0;
        boolean hasHeader = false;

        for (int i = 0; i < first.size(); i++) {
            if (COLUMN_NAMES.contains(first.get(i).toLowerCase(Locale.ROOT))) {
                column = i;
                hasHeader = true;
                break;
            }
        }

        List<String> channels = new ArrayList<>();
        for (int r = hasHeader ? 1 : 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            String value = column < row.size() ? row.get(column) : "";
            if (!value.isBlank()) channels.add(value);
        }

        return channels;
    }

    private static boolean isChannelLike(String value) {
        return value != null && CHANNEL_LIKE.matcher(value.strip()).find();
    }

    // --- Export ----------------------------------------------------------------------------------------------------------

    /** [language] names the Excel sheet (Английский / Русский / Узбекский), so the file imports back into that language. */
    byte[] write(Format format, String language, List<Line> lines) {
        return switch (format) {
            case XLSX -> writeExcel(language, lines);
            case CSV -> writeCsv(lines);
            case JSON -> writeJson(lines);
        };
    }

    /**
     * A file to fill in. Excel follows the owner's list (2026-10-13): one sheet per language, a title, then the table
     * № · Название канала · Ссылка на YouTube · Подписчики ≈ · Формат · Примечание · Источник официальности · Основание.
     * Only the link column is read on import.
     */
    byte[] template(Format format) {
        return switch (format) {
            case XLSX -> writeExcelTemplate();
            case CSV -> ("﻿channel\n" + String.join("\n", TEMPLATE) + "\n").getBytes(StandardCharsets.UTF_8);
            case JSON -> json.writerWithDefaultPrettyPrinter().writeValueAsBytes(Map.of("channels", TEMPLATE));
        };
    }

    private byte[] writeExcel(String language, List<Line> lines) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(SHEET_NAMES.getOrDefault(language, "channels"));
            Row header = sheet.createRow(0);
            for (int i = 0; i < EXPORT_HEADER.length; i++) header.createCell(i).setCellValue(EXPORT_HEADER[i]);

            for (int r = 0; r < lines.size(); r++) {
                Line line = lines.get(r);
                Row row = sheet.createRow(r + 1);
                String[] values = {
                    String.valueOf(r + 1),
                    line.title() == null ? "" : line.title(),
                    "https://www.youtube.com/channel/" + line.channelId(),
                    line.handle() == null ? "" : line.handle(),
                    line.addedAt() == null ? "" : line.addedAt(),
                };
                for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
            }
            for (int i = 0; i < EXPORT_HEADER.length; i++) sheet.autoSizeColumn(i);
            workbook.write(out);

            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] writeExcelTemplate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (Map.Entry<String, String> language : SHEET_NAMES.entrySet()) {
                Sheet sheet = workbook.createSheet(language.getValue());
                sheet.createRow(1).createCell(1).setCellValue("Детские мультканалы — " + language.getValue().toLowerCase(Locale.ROOT));
                Row header = sheet.createRow(3);
                for (int i = 0; i < TEMPLATE_HEADER.length; i++) header.createCell(i).setCellValue(TEMPLATE_HEADER[i]);
                Row example = sheet.createRow(4);
                example.createCell(0).setCellValue("1");
                example.createCell(1).setCellValue("Название канала");
                example.createCell(2).setCellValue(TEMPLATE.getFirst());
                for (int i = 0; i < TEMPLATE_HEADER.length; i++) sheet.setColumnWidth(i, (i == 2 ? 48 : 22) * 256);
            }
            workbook.write(out);

            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // UTF-8 with a BOM, so Excel opens Cyrillic and Uzbek titles correctly.
    private static byte[] writeCsv(List<Line> lines) {
        StringBuilder csv = new StringBuilder("﻿").append(String.join(",", HEADER)).append('\n');

        for (Line line : lines) {
            String[] values = values(line);
            for (int i = 0; i < values.length; i++) {
                if (i > 0) csv.append(',');
                csv.append('"').append(values[i].replace("\"", "\"\"")).append('"');
            }
            csv.append('\n');
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] writeJson(List<Line> lines) {
        List<Map<String, String>> items = lines.stream().map(line -> {
            String[] values = values(line);
            Map<String, String> item = new LinkedHashMap<>();
            for (int i = 0; i < HEADER.length; i++) item.put(HEADER[i], values[i]);
            return item;
        }).toList();

        return json.writerWithDefaultPrettyPrinter().writeValueAsBytes(Map.of("channels", items));
    }

    private static String[] values(Line line) {
        return new String[] {
            line.channelId(),
            line.title() == null ? "" : line.title(),
            line.handle() == null ? "" : line.handle(),
            "https://www.youtube.com/channel/" + line.channelId(),
            line.addedAt() == null ? "" : line.addedAt(),
        };
    }
}
