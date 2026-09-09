package demo.tripgo.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

// Bao ngoài kết quả phân trang theo hợp đồng { data, total, page, size }.
// page là số trang 1-based (khớp với query param của client), size là kích thước trang.
public record PageResponse<T>(
    List<T> data,
    long total,
    int page,
    int size
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
