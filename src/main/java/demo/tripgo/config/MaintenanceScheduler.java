package demo.tripgo.config;

import demo.tripgo.job.BackgroundJob;
import demo.tripgo.job.JobTrigger;
import demo.tripgo.job.MaintenanceJobs;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Lịch chạy của hai việc dọn dẹp.
//
// Tắt được bằng tasks.maintenance.enabled=false, và MẶC ĐỊNH TẮT trong profile test: job chạy nền
// xen vào giữa test sẽ sửa dữ liệu ngay dưới chân test và sinh lỗi chập chờn rất khó lần.
//
// @Async đẩy việc sang pool riêng: luồng lập lịch chỉ có một, một job chạy lâu sẽ làm job sau trễ
// theo. Trả về void vì không ai chờ kết quả.
@Component
@ConditionalOnProperty(prefix = "tasks.maintenance", name = "enabled", havingValue = "true",
    matchIfMissing = true)
public class MaintenanceScheduler {

    private final MaintenanceJobs jobs;

    public MaintenanceScheduler(MaintenanceJobs jobs) {
        this.jobs = jobs;
    }

    // fixedDelay chứ không fixedRate: đếm từ lúc lần trước KẾT THÚC, nên job chạy lâu không bị
    // xếp chồng lên chính nó.
    //
    // Nội dung job nằm ở MaintenanceJobs (dùng chung với nút "Chạy ngay"); JobRunTracker ghi lại
    // từng lần chạy cho màn hình "Job nền" và bắt lỗi của luồng job.
    @Async(AsyncConfig.TASK_EXECUTOR)
    @Scheduled(
        fixedDelayString = "${tasks.maintenance.cancel-expired-delay:PT1H}",
        initialDelayString = "${tasks.maintenance.initial-delay:PT2M}")
    public void cancelExpiredBookings() {
        jobs.run(BackgroundJob.CANCEL_EXPIRED_BOOKINGS, JobTrigger.SCHEDULED);
    }

    @Async(AsyncConfig.TASK_EXECUTOR)
    @Scheduled(
        fixedDelayString = "${tasks.maintenance.refresh-rating-delay:PT6H}",
        initialDelayString = "${tasks.maintenance.initial-delay:PT2M}")
    public void refreshRatings() {
        jobs.run(BackgroundJob.REFRESH_RATINGS, JobTrigger.SCHEDULED);
    }
}
