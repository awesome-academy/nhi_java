package demo.tripgo.admin;

import demo.tripgo.job.BackgroundJob;
import demo.tripgo.job.JobRunState;

import java.time.LocalDateTime;

// Một thẻ trên màn hình "Job nền". nextRunAt null = không ước tính được (lịch đang tắt, hoặc
// job đang chạy dở).
public record JobView(
    BackgroundJob job,
    String description,
    JobRunState state,
    String interval,
    LocalDateTime nextRunAt,
    boolean scheduleEnabled
) {
}
