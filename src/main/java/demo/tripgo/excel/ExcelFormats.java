package demo.tripgo.excel;

// Định dạng hiển thị dùng chung cho @ExcelColumn(format = ...). Chỉ đổi cách Excel HIỂN THỊ ô,
// giá trị bên trong vẫn là số: cộng/lọc/sắp xếp được và nhập lại file không bị ảnh hưởng.
public final class ExcelFormats {

    // 4500000 hiện thành "4.500.000 VNĐ" (dấu phân cách nghìn theo cài đặt vùng của máy mở file).
    public static final String VND = "#,##0 \"VNĐ\"";

    // Luôn một chữ số thập phân: 4 hiện thành "4.0" để cột điểm thẳng hàng.
    public static final String ONE_DECIMAL = "0.0";

    private ExcelFormats() {
    }
}
