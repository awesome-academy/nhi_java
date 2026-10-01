package demo.tripgo.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

// Ghi lại lần chạy gần nhất của từng job nền, để màn hình "Job nền" hiện được.
//
// Lưu TRONG BỘ NHỚ: khởi động lại ứng dụng là mất, nhưng job chạy lại sau vài phút nên chấp
// nhận được, đổi lại không phải thêm bảng.
//
// Đây cũng là chỗ xử lý lỗi của luồng job: lỗi bị bắt ở đây, ghi log + ghi vào trạng thái, KHÔNG
// ném tiếp. Ném ra khỏi một hàm @Async trả void thì không ai nhận được, lỗi chỉ còn trong log.
@Component
public class JobRunTracker {

    private static final Logger log = LoggerFactory.getLogger(JobRunTracker.class);

    private final Map<BackgroundJob, JobRunState> states = new ConcurrentHashMap<>();
    // Mốc ước tính lần chạy ĐẦU TIÊN (= lúc khởi động + initial-delay) khi job chưa chạy lần nào.
    private final LocalDateTime startedAt = LocalDateTime.now();

    public JobRunTracker() {
        for (BackgroundJob job : BackgroundJob.values()) {
            states.put(job, JobRunState.neverRun());
        }
    }

    // Chạy work và ghi nhận kết quả. work trả về một câu tóm tắt cho màn hình.
    // Trả false nếu job đang chạy dở (lịch và "Chạy ngay" trùng nhau): không chạy chồng.
    public boolean run(BackgroundJob job, JobTrigger trigger, Supplier<String> work) {
        if (!tryStart(job, trigger)) {
            log.info("Bỏ qua {} ({}): lần chạy trước chưa xong", job.getTitle(), trigger.getLabel());
            return false;
        }

        long startNanos = System.nanoTime();
        try {
            String summary = work.get();
            finish(job, JobRunState.Status.SUCCESS, startNanos, summary, null);
        } catch (RuntimeException exception) {
            log.error("Job {} lỗi trên luồng {}", job.getTitle(), Thread.currentThread().getName(), exception);
            finish(job, JobRunState.Status.FAILED, startNanos, null, describe(exception));
        }
        return true;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public JobRunState state(BackgroundJob job) {
        return states.get(job);
    }

    public Map<BackgroundJob, JobRunState> snapshot() {
        return new EnumMap<>(states);
    }

    // compute là thao tác nguyên tử trên một khoá của ConcurrentHashMap: hai luồng cùng bấm thì
    // chỉ một luồng chuyển được sang RUNNING.
    private boolean tryStart(BackgroundJob job, JobTrigger trigger) {
        boolean[] started = {false};
        states.compute(job, (key, current) -> {
            if (current.isRunning()) {
                return current;
            }
            started[0] = true;
            return new JobRunState(
                JobRunState.Status.RUNNING, trigger, LocalDateTime.now(), current.lastFinishedAt(),
                current.lastScheduledFinishedAt(), current.durationMillis(),
                Thread.currentThread().getName(), current.summary(), null, current.runCount());
        });
        return started[0];
    }

    private void finish(
        BackgroundJob job, JobRunState.Status status, long startNanos, String summary, String error
    ) {
        LocalDateTime now = LocalDateTime.now();
        long durationMillis = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
        states.compute(job, (key, current) -> new JobRunState(
            status, current.trigger(), current.lastStartedAt(), now,
            current.trigger() == JobTrigger.SCHEDULED ? now : current.lastScheduledFinishedAt(),
            durationMillis, current.threadName(), summary, error, current.runCount() + 1));
    }

    private static String describe(RuntimeException exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
