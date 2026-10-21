package uz.mittisoftware.mittigo.admin.web;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** The server's admin API answers (BACKEND_MVP §6.7), as the pages read them. */
public final class Models {

    private Models() {
    }

    public record Items<T>(List<T> items) {
    }

    public record Channel(String channelId, String title, String handle, String thumbnailUrl, Integer videoCount, Integer playlistCount) {
    }

    public record Recommendation(UUID id, String language, String channelId, int position, boolean isActive, Channel channel) {
    }

    public record Contact(String type, String value, String uri, int position) {
    }

    public record ReleaseNote(UUID id, String version, LocalDate releasedOn, String textUz, String textRu, String textEn) {
    }

    public record LegalLink(String key, String url) {
    }

    public record Plan(int periodMonths, String priceUsd, int position, boolean isActive, String storeProductId) {
    }

    public record Setting(String key, String value) {
    }

    public record Stats(
            long installs,
            long activeInstalls30d,
            long accounts,
            int quotaUsedToday,
            int quotaPerDay,
            boolean youTubeConfigured,
            long cachedChannels,
            long cachedPlaylists,
            long cachedVideos,
            long recommendations
    ) {
        public int quotaPercent() {
            return quotaPerDay == 0 ? 0 : Math.min(100, quotaUsedToday * 100 / quotaPerDay);
        }
    }

    public record PromoCode(UUID id, String code, String period, Integer maxRedemptions, int redeemedCount, LocalDate validUntil,
                            boolean isActive, String note, OffsetDateTime createdAt, OffsetDateTime lastRedeemedAt) {
        public boolean isUsedUp() {
            return maxRedemptions != null && redeemedCount >= maxRedemptions;
        }

        /** [validUntil] is the last day the code works (inclusive). */
        public boolean isExpired() {
            return validUntil != null && validUntil.isBefore(LocalDate.now());
        }

        /** Can a parent still enter it: switched on, not used up, not expired. */
        public boolean isWorking() {
            return isActive && !isUsedUp() && !isExpired();
        }

        /** Why it's in the archive, or "Активен". */
        public String status() {
            if (!isActive) return "Выключен";
            if (isUsedUp()) return "Исчерпан";
            if (isExpired()) return "Истёк";

            return "Активен";
        }
    }

    public record OfficialChannel(UUID id, String language, String channelId, OffsetDateTime addedAt, Channel channel) {
    }

    public record ImportProblem(int row, String input, String reason) {
    }

    public record ImportResult(int added, int duplicates, List<ImportProblem> problems) {
    }

    /** Today's YouTube quota: used / limit, the calls grouped by our endpoint + YouTube method, the newest calls. */
    public record Quota(int used, int limit, List<QuotaUsage> usage, List<QuotaCall> recent) {
    }

    public record QuotaUsage(String source, String method, int calls, int units) {
    }

    public record QuotaCall(OffsetDateTime at, String source, String method, int units) {
    }

    /** An admin-panel account; [isMe] = the signed-in admin, [isMain] = the main admin (from the server secrets). */
    public record AdminAccount(UUID id, String login, boolean isActive, OffsetDateTime lastLoginAt, boolean isMe, boolean isMain) {
    }

    public record AuditEntry(long id, String admin, String action, String details, String channelTitle, OffsetDateTime createdAt) {
    }
}
