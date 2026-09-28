package demo.tripgo.config;

import demo.tripgo.service.MaintenanceService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
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
@EnableConfigurationProperties(MaintenanceScheduler.MaintenanceProperties.class)
@ConditionalOnProperty(prefix = "tasks.maintenance", name = "enabled", havingValue = "true",
    matchIfMissing = true)
public class MaintenanceScheduler {

    private final MaintenanceService maintenanceService;
    private final MaintenanceProperties properties;

    public MaintenanceScheduler(
        MaintenanceService maintenanceService,
        MaintenanceProperties properties
    ) {
        this.maintenanceService = maintenanceService;
        this.properties = properties;
    }

    // fixedDelay chứ không fixedRate: đếm từ lúc lần trước KẾT THÚC, nên job chạy lâu không bị
    // xếp chồng lên chính nó.
    @Async(AsyncConfig.TASK_EXECUTOR)
    @Scheduled(
        fixedDelayString = "${tasks.maintenance.cancel-expired-delay:PT1H}",
        initialDelayString = "${tasks.maintenance.initial-delay:PT2M}")
    public void cancelExpiredBookings() {
        maintenanceService.cancelExpiredPendingBookings(properties.expireAfterHours());
    }

    @Async(AsyncConfig.TASK_EXECUTOR)
    @Scheduled(
        fixedDelayString = "${tasks.maintenance.refresh-rating-delay:PT6H}",
        initialDelayString = "${tasks.maintenance.initial-delay:PT2M}")
    public void refreshRatings() {
        maintenanceService.refreshStaleRatings();
    }

    @ConfigurationProperties(prefix = "tasks.maintenance")
    public record MaintenanceProperties(
        @DefaultValue("true") boolean enabled,
        // 72 giờ: đủ dài để khách kịp chuyển khoản, đủ ngắn để chỗ không bị giữ vô hạn.
        @DefaultValue("72") int expireAfterHours
    ) {
    }
}
