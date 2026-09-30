package demo.tripgo.admin.excel;

import demo.tripgo.excel.ExcelColumn;
import demo.tripgo.excel.ExcelFormats;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// Cột trùng khớp với TourImportRow để file xuất ra sửa xong nhập lại được luôn. Vì vậy đơn vị
// VNĐ nằm ở định dạng ô chứ không ở tiêu đề: đổi thành "Giá (VNĐ)" thì nhập lại sẽ báo thiếu cột "Giá".
@Getter
@Setter
public class TourExportRow {

    @ExcelColumn(header = "ID", order = 1)
    private Long id;

    @ExcelColumn(header = "Tên tour", order = 2)
    private String title;

    @ExcelColumn(header = "Slug", order = 3)
    private String slug;

    @ExcelColumn(header = "Điểm đến", order = 4)
    private String destination;

    @ExcelColumn(header = "Loại hình", order = 5)
    private String category;

    @ExcelColumn(header = "Giá", order = 6, format = ExcelFormats.VND)
    private BigDecimal price;

    @ExcelColumn(header = "Giá khuyến mãi", order = 7, format = ExcelFormats.VND)
    private BigDecimal discountPrice;

    @ExcelColumn(header = "Số ngày", order = 8)
    private Integer durationDays;

    // ExcelExportService đã làm tròn 1 chữ số; format chỉ để 4 hiện thành 4.0.
    @ExcelColumn(header = "Đánh giá", order = 9, format = ExcelFormats.ONE_DECIMAL)
    private Double rating;

    @ExcelColumn(header = "Lượt đánh giá", order = 10)
    private Integer reviewCount;
}
