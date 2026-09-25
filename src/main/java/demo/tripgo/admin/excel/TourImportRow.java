package demo.tripgo.admin.excel;

import demo.tripgo.excel.ExcelColumn;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// Một dòng trong file nhập tour. Cột trùng tên với file xuất ra, nên xuất -> sửa -> nhập lại được.
//
// Điểm đến và loại hình nhận SLUG chứ không nhận tên: tên có dấu, dễ gõ sai và có thể trùng, còn
// slug là định danh ổn định mà file xuất ra cũng đang dùng.
@Getter
@Setter
public class TourImportRow {

    @ExcelColumn(header = "Tên tour", order = 1, required = true)
    private String title;

    @ExcelColumn(header = "Slug", order = 2)
    private String slug;

    @ExcelColumn(header = "Điểm đến", order = 3, required = true)
    private String destinationSlug;

    @ExcelColumn(header = "Loại hình", order = 4, required = true)
    private String categorySlug;

    @ExcelColumn(header = "Giá", order = 5, required = true)
    private BigDecimal price;

    @ExcelColumn(header = "Giá khuyến mãi", order = 6)
    private BigDecimal discountPrice;

    @ExcelColumn(header = "Số ngày", order = 7, required = true)
    private Integer durationDays;

    @ExcelColumn(header = "Số khách tối đa", order = 8, required = true)
    private Integer maxGuests;

    @ExcelColumn(header = "Mô tả", order = 9)
    private String description;
}
