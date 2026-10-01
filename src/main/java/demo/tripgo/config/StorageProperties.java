package demo.tripgo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

// Nơi lưu ảnh admin tải lên. Cố ý đặt NGOÀI src/main/resources/static: thư mục đó được đóng gói
// vào jar lúc build, nên file ghi vào đó lúc chạy sẽ không phục vụ được và mất khi build lại.
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
    // Đường dẫn thư mục trên đĩa (tương đối so với nơi chạy ứng dụng, hoặc tuyệt đối).
    @DefaultValue("./uploads") String uploadDir,

    // Tiền tố URL để trình duyệt lấy ảnh; ResourceHandler ánh xạ nó về uploadDir.
    @DefaultValue("/uploads") String publicPath,

    @DefaultValue("5242880") long maxFileSizeBytes,

    @DefaultValue({"image/jpeg", "image/png", "image/webp", "image/gif"})
    List<String> allowedContentTypes
) {
}
