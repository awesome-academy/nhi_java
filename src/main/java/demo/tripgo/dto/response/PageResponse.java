package demo.tripgo.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

// Bao ngoài kết quả phân trang theo hợp đồng { data, total, page, limit }.
// page là số trang 1-based (khớp với query param của client), limit là kích thước trang.
public record PageResponse<T>(
    List<T> data,
    long total,
    int page,
    int limit
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
            page.getContent(),
            page.getTotalElements(),
            page.getNumber() + 1,
            page.getSize()
        );
    }
}
