package demo.tripgo.service;

import demo.tripgo.config.StorageProperties;
import demo.tripgo.exception.InvalidRequestParameterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

// Lưu ảnh admin tải lên xuống đĩa và trả về URL công khai để gắn vào DB.
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    // Phần mở rộng suy ra từ content type chứ KHÔNG lấy từ tên file người dùng gửi lên:
    // tên file là dữ liệu do client kiểm soát, dùng thẳng là mở đường cho path traversal
    // (../../etc/passwd) và cho việc đặt đuôi .jsp/.html nhằm lừa trình duyệt thực thi.
    private static final Map<String, String> EXTENSION_BY_TYPE = Map.of(
        "image/jpeg", ".jpg",
        "image/png", ".png",
        "image/webp", ".webp",
        "image/gif", ".gif"
    );

    private final StorageProperties properties;
    private final Path root;

    public FileStorageService(StorageProperties properties) {
        this.properties = properties;
        this.root = Paths.get(properties.uploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new UncheckedIOException("Không tạo được thư mục lưu ảnh: " + root, exception);
        }
        log.info("Ảnh tải lên được lưu tại {}", root);
    }

    // Trả về URL công khai dạng /uploads/2026/09/<uuid>.jpg, hoặc null nếu không có file nào được chọn.
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        validate(file);

        // Chia theo năm/tháng để một thư mục không phình ra hàng chục nghìn file.
        LocalDate today = LocalDate.now();
        String relativeDir = "%d/%02d".formatted(today.getYear(), today.getMonthValue());
        String fileName = UUID.randomUUID() + extensionFor(file);

        try {
            Path directory = root.resolve(relativeDir);
            Files.createDirectories(directory);
            Path target = directory.resolve(fileName);
            try (var input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return properties.publicPath() + "/" + relativeDir + "/" + fileName;
        } catch (IOException exception) {
            throw new UncheckedIOException("Không lưu được ảnh " + fileName, exception);
        }
    }

    // Xoá file cũ khi ảnh bị thay/gỡ. Không tìm thấy thì bỏ qua: DB là nguồn sự thật, file thừa
    // trên đĩa không được phép làm hỏng thao tác của admin.
    public void delete(String publicUrl) {
        Path target = resolvePublicUrl(publicUrl);
        if (target == null) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            log.warn("Không xoá được file {}: {}", target, exception.getMessage());
        }
    }

    // Chỉ chấp nhận URL do chính service này sinh ra, và kết quả phải nằm trong thư mục gốc —
    // chặn trường hợp giá trị trong DB bị sửa thành ../../ để xoá file ngoài vùng cho phép.
    private Path resolvePublicUrl(String publicUrl) {
        if (publicUrl == null || !publicUrl.startsWith(properties.publicPath() + "/")) {
            return null;
        }
        String relative = publicUrl.substring(properties.publicPath().length() + 1);
        Path resolved = root.resolve(relative).normalize();
        return resolved.startsWith(root) ? resolved : null;
    }

    private void validate(MultipartFile file) {
        if (file.getSize() > properties.maxFileSizeBytes()) {
            throw new InvalidRequestParameterException(
                "Ảnh vượt quá %d MB".formatted(properties.maxFileSizeBytes() / 1024 / 1024));
        }
        String contentType = normalizeContentType(file.getContentType());
        if (!properties.allowedContentTypes().contains(contentType)) {
            throw new InvalidRequestParameterException(
                "Chỉ chấp nhận ảnh JPG, PNG, WEBP hoặc GIF");
        }
    }

    private String extensionFor(MultipartFile file) {
        return EXTENSION_BY_TYPE.getOrDefault(normalizeContentType(file.getContentType()), ".bin");
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        // Bỏ phần tham số kiểu "image/jpeg; charset=..." và chuẩn hoá hoa/thường.
        int separator = contentType.indexOf(';');
        String base = separator == -1 ? contentType : contentType.substring(0, separator);
        return base.trim().toLowerCase(Locale.ROOT);
    }
}
