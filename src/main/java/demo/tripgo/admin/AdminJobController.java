package demo.tripgo.admin;

import demo.tripgo.exception.ResourceNotFoundException;
import demo.tripgo.job.BackgroundJob;
import demo.tripgo.job.JobRunState;
import demo.tripgo.job.JobRunTracker;
import demo.tripgo.job.MaintenanceJobs;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

// Màn hình "Job nền": lần chạy cuối, kết quả, lỗi, lần chạy kế tiếp của từng job, và nút
// "Chạy ngay". Trạng thái lấy từ JobRunTracker (trong bộ nhớ).
@Controller
@RequestMapping("/admin/jobs")
public class AdminJobController {

    private final JobRunTracker tracker;
    private final MaintenanceJobs jobs;
    private final Environment environment;

    public AdminJobController(JobRunTracker tracker, MaintenanceJobs jobs, Environment environment) {
        this.tracker = tracker;
        this.jobs = jobs;
        this.environment = environment;
    }

    @GetMapping
    public String list(Model model) {
        boolean scheduleEnabled = environment.getProperty("tasks.maintenance.enabled", Boolean.class, true);
        Duration initialDelay = duration("tasks.maintenance.initial-delay", "PT2M");

        List<JobView> views = Arrays.stream(BackgroundJob.values())
            .map(job -> {
                JobRunState state = tracker.state(job);
                Duration delay = duration(job.getDelayProperty(), job.getDefaultDelay());
                return new JobView(job, describe(job), state, formatEvery(delay),
                    scheduleEnabled ? nextRun(state, delay, initialDelay) : null, scheduleEnabled);
            })
            .toList();

        model.addAttribute("activeMenu", "jobs");
        model.addAttribute("pageHeading", "Job nền");
        model.addAttribute("jobs", views);
        model.addAttribute("scheduleEnabled", scheduleEnabled);
        return "admin/jobs/list";
    }

    @PostMapping("/{slug}/run")
    public String runNow(@PathVariable String slug, RedirectAttributes redirect) {
        BackgroundJob job = BackgroundJob.fromSlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("job"));

        if (tracker.state(job).isRunning()) {
            redirect.addFlashAttribute("flashMessage", "\"" + job.getTitle() + "\" đang chạy, chờ xong rồi thử lại");
            redirect.addFlashAttribute("flashType", "error");
        } else {
            jobs.runNow(job);
            redirect.addFlashAttribute("flashMessage",
                "Đã bắt đầu chạy \"" + job.getTitle() + "\" trên luồng nền. Tải lại trang để xem kết quả.");
            redirect.addFlashAttribute("flashType", "success");
        }
        return "redirect:/admin/jobs";
    }

    private String describe(BackgroundJob job) {
        return switch (job) {
            case CANCEL_EXPIRED_BOOKINGS -> "Huỷ các đơn \"Chờ xác nhận\" quá %d giờ và hoàn lại chỗ cho ngày khởi hành."
                .formatted(jobs.getExpireAfterHours());
            case REFRESH_RATINGS -> "So điểm trung bình của từng tour với bảng đánh giá, sửa những tour bị lệch.";
        };
    }

    // fixedDelay: lần kế tiếp = lần chạy THEO LỊCH trước đó kết thúc + nhịp. Chưa chạy lần nào thì
    // = lúc khởi động + initial-delay. Chỉ là ước tính, nên trang ghi "khoảng".
    private LocalDateTime nextRun(JobRunState state, Duration delay, Duration initialDelay) {
        if (state.isRunning()) {
            return null;
        }
        return state.lastScheduledFinishedAt() != null
            ? state.lastScheduledFinishedAt().plus(delay)
            : tracker.getStartedAt().plus(initialDelay);
    }

    // Đọc đúng chuỗi mà @Scheduled đọc; DurationStyle hiểu cả "PT2M" lẫn "2m".
    private Duration duration(String key, String fallback) {
        return DurationStyle.detectAndParse(environment.getProperty(key, fallback));
    }

    private static String formatEvery(Duration delay) {
        long seconds = delay.toSeconds();
        if (seconds > 0 && seconds % 3600 == 0) {
            return "mỗi " + seconds / 3600 + " giờ";
        }
        if (seconds > 0 && seconds % 60 == 0) {
            return "mỗi " + seconds / 60 + " phút";
        }
        return "mỗi " + seconds + " giây";
    }
}
