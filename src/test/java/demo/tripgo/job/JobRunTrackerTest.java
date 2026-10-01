package demo.tripgo.job;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// Phần ghi nhận + xử lý lỗi của luồng job, không cần Spring.
class JobRunTrackerTest {

    private final JobRunTracker tracker = new JobRunTracker();
    private static final BackgroundJob JOB = BackgroundJob.CANCEL_EXPIRED_BOOKINGS;

    @Test
    void startsAsNeverRun() {
        assertThat(tracker.state(JOB).status()).isEqualTo(JobRunState.Status.NEVER_RUN);
        assertThat(tracker.state(JOB).runCount()).isZero();
    }

    @Test
    void recordsSuccessfulRun() {
        assertThat(tracker.run(JOB, JobTrigger.SCHEDULED, () -> "Đã huỷ 3")).isTrue();

        JobRunState state = tracker.state(JOB);
        assertThat(state.status()).isEqualTo(JobRunState.Status.SUCCESS);
        assertThat(state.summary()).isEqualTo("Đã huỷ 3");
        assertThat(state.threadName()).isEqualTo(Thread.currentThread().getName());
        assertThat(state.lastStartedAt()).isNotNull();
        assertThat(state.lastFinishedAt()).isNotNull();
        assertThat(state.lastScheduledFinishedAt()).isEqualTo(state.lastFinishedAt());
        assertThat(state.runCount()).isEqualTo(1);
    }

    // Lỗi bị bắt và ghi lại, KHÔNG ném ra ngoài; lần chạy sau vẫn chạy và xoá lỗi cũ.
    @Test
    void failureIsRecordedAndNextRunStillWorks() {
        tracker.run(JOB, JobTrigger.SCHEDULED, () -> {
            throw new IllegalStateException("mất kết nối DB");
        });

        JobRunState failed = tracker.state(JOB);
        assertThat(failed.status()).isEqualTo(JobRunState.Status.FAILED);
        assertThat(failed.error()).isEqualTo("IllegalStateException: mất kết nối DB");

        tracker.run(JOB, JobTrigger.SCHEDULED, () -> "ổn rồi");

        JobRunState recovered = tracker.state(JOB);
        assertThat(recovered.status()).isEqualTo(JobRunState.Status.SUCCESS);
        assertThat(recovered.error()).isNull();
        assertThat(recovered.runCount()).isEqualTo(2);
    }

    // Lịch fixedDelay tính từ lần chạy THEO LỊCH: bấm "Chạy ngay" không được dời mốc đó.
    @Test
    void manualRunDoesNotMoveScheduledMark() {
        tracker.run(JOB, JobTrigger.SCHEDULED, () -> "lịch");
        var scheduledMark = tracker.state(JOB).lastScheduledFinishedAt();

        tracker.run(JOB, JobTrigger.MANUAL, () -> "tay");

        assertThat(tracker.state(JOB).trigger()).isEqualTo(JobTrigger.MANUAL);
        assertThat(tracker.state(JOB).lastScheduledFinishedAt()).isEqualTo(scheduledMark);
    }

    // Lịch và "Chạy ngay" trùng nhau: lần thứ hai bị bỏ qua chứ không chạy chồng.
    @Test
    void doesNotRunTwiceAtTheSameTime() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> first = pool.submit(() -> tracker.run(JOB, JobTrigger.SCHEDULED, () -> {
                started.countDown();
                await(release);
                return "xong";
            }));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(tracker.state(JOB).isRunning()).isTrue();

            assertThat(tracker.run(JOB, JobTrigger.MANUAL, () -> "không được chạy")).isFalse();

            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(tracker.state(JOB).summary()).isEqualTo("xong");
            assertThat(tracker.state(JOB).runCount()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
