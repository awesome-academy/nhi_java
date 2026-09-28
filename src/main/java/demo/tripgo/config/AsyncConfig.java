package demo.tripgo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

// Bật job định kỳ và chạy nền.
//
// Pool riêng cho việc nền thay vì dùng executor mặc định: executor mặc định của Spring Boot là
// SimpleAsyncTaskExecutor — nó tạo MỘT LUỒNG MỚI cho mỗi lần gọi, không giới hạn. Một job chạy
// lâu gặp lúc nhiều việc dồn lại sẽ sinh luồng không kiểm soát.
@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(AsyncConfig.TaskProperties.class)
public class AsyncConfig {

    public static final String TASK_EXECUTOR = "tripgoTaskExecutor";

    @Bean(TASK_EXECUTOR)
    public Executor tripgoTaskExecutor(TaskProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.corePoolSize());
        executor.setMaxPoolSize(properties.maxPoolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("tripgo-task-");
        // Hàng đợi đầy thì chạy ngay trên luồng gọi thay vì vứt việc đi: chậm còn hơn mất việc.
        executor.setRejectedExecutionHandler(
            new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        // Chờ việc đang chạy xong mới tắt, để job không bị cắt ngang giữa chừng lúc deploy.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @ConfigurationProperties(prefix = "tasks.executor")
    public record TaskProperties(
        @DefaultValue("2") int corePoolSize,
        @DefaultValue("4") int maxPoolSize,
        @DefaultValue("50") int queueCapacity
    ) {
    }
}
