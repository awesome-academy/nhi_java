package demo.tripgo.job;

import java.time.LocalDateTime;

// Ảnh chụp trạng thái MỘT job tại một thời điểm. Bất biến: tracker thay cả bản ghi mỗi lần đổi,
// nên luồng đọc (trang admin) không bao giờ thấy bản ghi đang sửa dở từ luồng job.
//
// lastScheduledFinishedAt tách riêng khỏi lastFinishedAt: lịch fixedDelay tính lần chạy kế tiếp
// từ lần chạy THEO LỊCH trước đó, lần bấm "Chạy ngay" không dời lịch.
public record JobRunState(
    Status status,
    JobTrigger trigger,
    LocalDateTime lastStartedAt,
    LocalDateTime lastFinishedAt,
    LocalDateTime lastScheduledFinishedAt,
    long durationMillis,
    String threadName,
    String summary,
    String error,
    long runCount
) {

    public enum Status {
        NEVER_RUN("Chưa chạy"),
        RUNNING("Đang chạy"),
        SUCCESS("Thành công"),
        FAILED("Lỗi");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public static JobRunState neverRun() {
        return new JobRunState(Status.NEVER_RUN, null, null, null, null, 0, null, null, null, 0);
    }

    public boolean isRunning() {
        return status == Status.RUNNING;
    }
}
