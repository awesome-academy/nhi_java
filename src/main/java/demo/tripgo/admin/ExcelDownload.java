package demo.tripgo.admin;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

// Dựng response tải file. Gom vào một chỗ vì phần header dễ sai và lặp ở nhiều controller.
final class ExcelDownload {

    private static final MediaType XLSX =
        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private ExcelDownload() {
    }

    static ResponseEntity<Resource> of(byte[] content, String baseName) {
        String fileName = baseName + "-" + LocalDate.now().format(FILE_DATE) + ".xlsx";

        // ContentDisposition.builder tự sinh cả filename* (RFC 5987) nên tên file có dấu tiếng
        // Việt không bị trình duyệt hiển thị thành ký tự lạ.
        ContentDisposition disposition = ContentDisposition.attachment()
            .filename(fileName, StandardCharsets.UTF_8)
            .build();

        return ResponseEntity.ok()
            .contentType(XLSX)
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .contentLength(content.length)
            .body(new ByteArrayResource(content));
    }
}
