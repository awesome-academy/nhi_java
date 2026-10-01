package demo.tripgo.job;

import demo.tripgo.config.AsyncConfig;
import demo.tripgo.event.ChartUpdatePublisher;
import demo.tripgo.service.MaintenanceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

// Nội dung của hai job nền, dùng chung cho lịch @Scheduled (MaintenanceScheduler) và nút
// "Chạy ngay" trên màn hình admin — để hai đường chạy KHÔNG thể lệch nhau.
//
// Không phụ thuộc MaintenanceScheduler: tắt lịch tự động (TASKS_ENABLED=false) thì admin vẫn bấm
// chạy tay được.
@Component
public class MaintenanceJobs {

    private final MaintenanceService maintenanceService;
    private final JobRunTracker tracker;
    private final ChartUpdatePublisher chartUpdates;
    private final int expireAfterHours;

    public MaintenanceJobs(
        MaintenanceService maintenanceService,
        JobRunTracker tracker,
        ChartUpdatePublisher chartUpdates,
        @Value("${tasks.maintenance.expire-after-hours:72}") int expireAfterHours
    ) {
        this.maintenanceService = maintenanceService;
        this.tracker = tracker;
        this.chartUpdates = chartUpdates;
        this.expireAfterHours = expireAfterHours;
    }

    public int getExpireAfterHours() {
        return expireAfterHours;
    }

    public boolean run(BackgroundJob job, JobTrigger trigger) {
        return switch (job) {
            case CANCEL_EXPIRED_BOOKINGS -> tracker.run(job, trigger, () -> {
                MaintenanceService.ExpiredCancellation result =
                    maintenanceService.cancelExpiredPendingBookings(expireAfterHours);
                String summary = "Tìm thấy %d đơn quá %d giờ, đã huỷ %d"
                    .formatted(result.found(), expireAfterHours, result.cancelled());
                // Job xong -> broadcast cho biểu đồ (doanh thu đổi vì đơn huỷ không còn được tính).
                chartUpdates.jobFinished(job.getTitle(), result.cancelled(), summary);
                return summary;
            });
            case REFRESH_RATINGS -> tracker.run(job, trigger, () ->
                "Tính lại %d tour bị lệch số liệu".formatted(maintenanceService.refreshStaleRatings()));
        };
    }

    // "Chạy ngay": đẩy sang pool nền để request của admin trả về ngay, không phải chờ job xong.
    @Async(AsyncConfig.TASK_EXECUTOR)
    public void runNow(BackgroundJob job) {
        run(job, JobTrigger.MANUAL);
    }
}
