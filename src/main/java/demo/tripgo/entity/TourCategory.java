package demo.tripgo.entity;

import java.util.Locale;

// Loại hình tour dùng cho bộ lọc category.
// slug là giá trị client gửi/nhận qua API (chữ thường), displayName là nhãn tiếng Việt để hiển thị.
// Để enum thay vì bảng vì danh sách cố định, do code định nghĩa, không đổi lúc chạy.
public enum TourCategory {
    BEACH("Biển đảo"),
    MOUNTAIN("Núi rừng"),
    CITY("Thành phố"),
    TREKKING("Trekking"),
    CRUISE("Du thuyền"),
    CULTURAL("Văn hoá");

    private final String displayName;

    TourCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getSlug() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String getDisplayName() {
        return displayName;
    }
}
