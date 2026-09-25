package demo.tripgo.entity;

public enum BookingStatus {
    PENDING("Chờ xác nhận"),
    CONFIRMED("Đã xác nhận"),
    CANCELLED("Đã huỷ");

    // Nhãn hiển thị ở khu quản trị. Đặt ngay trên enum để giao diện và bộ lọc dùng chung một
    // nguồn, thêm trạng thái mới không phải đi sửa rải rác ở template.
    private final String label;

    BookingStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
