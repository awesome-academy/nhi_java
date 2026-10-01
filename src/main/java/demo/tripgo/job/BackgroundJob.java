package demo.tripgo.job;

import java.util.Arrays;
import java.util.Optional;

// Các job nền hiển thị trên màn hình "Job nền". slug dùng trên URL nút "Chạy ngay";
// delayProperty là khoá cấu hình nhịp chạy mà @Scheduled của job đó đọc.
public enum BackgroundJob {

    CANCEL_EXPIRED_BOOKINGS(
        "cancel-expired-bookings",
        "Huỷ đơn quá hạn",
        "tasks.maintenance.cancel-expired-delay",
        "PT1H"),

    REFRESH_RATINGS(
        "refresh-ratings",
        "Tính lại điểm đánh giá",
        "tasks.maintenance.refresh-rating-delay",
        "PT6H");

    private final String slug;
    private final String title;
    private final String delayProperty;
    private final String defaultDelay;

    BackgroundJob(String slug, String title, String delayProperty, String defaultDelay) {
        this.slug = slug;
        this.title = title;
        this.delayProperty = delayProperty;
        this.defaultDelay = defaultDelay;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getDelayProperty() {
        return delayProperty;
    }

    public String getDefaultDelay() {
        return defaultDelay;
    }

    public static Optional<BackgroundJob> fromSlug(String slug) {
        return Arrays.stream(values()).filter(job -> job.slug.equals(slug)).findFirst();
    }
}
