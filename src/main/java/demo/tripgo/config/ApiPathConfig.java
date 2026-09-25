package demo.tripgo.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

// Tiền tố /api/v1 gắn theo package thay vì bằng server.servlet.context-path, vì context-path bọc
// TOÀN BỘ ứng dụng — kể cả khu quản trị /admin/** vốn không phải là API.
// Controller trong demo.tripgo.controller vẫn khai @RequestMapping("/tours") như cũ và URL thật
// vẫn là /api/v1/tours; controller ở package khác (admin) không bị thêm gì.
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class ApiPathConfig implements WebMvcConfigurer {

    public static final String API_PREFIX = "/api/v1";

    // Package chứa controller của API công khai. Đặt ở đây để SecurityConfig và RateLimitFilter
    // dùng chung một nguồn sự thật với cấu hình định tuyến.
    private static final String API_CONTROLLER_PACKAGE = "demo.tripgo.controller";

    private final StorageProperties storageProperties;

    public ApiPathConfig(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    // Ảnh tải lên nằm ngoài classpath nên phải khai resource handler trỏ vào thư mục trên đĩa;
    // không có nó thì URL /uploads/... trả 404 dù file đã ghi xuống.
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(storageProperties.uploadDir()).toAbsolutePath().normalize()
            .toUri().toString();
        registry.addResourceHandler(storageProperties.publicPath() + "/**")
            .addResourceLocations(location);
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(
            API_PREFIX,
            HandlerTypePredicate.forBasePackage(API_CONTROLLER_PACKAGE));
    }
}
