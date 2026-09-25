package demo.tripgo.admin.excel;

import demo.tripgo.excel.ExcelColumn;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

// Một dòng trong file Excel đơn đặt. Chỉ cần gắn @ExcelColumn, ExcelMapper lo phần còn lại.
@Getter
@Setter
public class BookingExportRow {

    @ExcelColumn(header = "Mã đơn", order = 1)
    private String code;

    @ExcelColumn(header = "Khách", order = 2)
    private String customerName;

    @ExcelColumn(header = "Email", order = 3)
    private String customerEmail;

    @ExcelColumn(header = "Tour", order = 4)
    private String tourTitle;

    @ExcelColumn(header = "Ngày đi", order = 5)
    private LocalDate departureDate;

    @ExcelColumn(header = "Số khách", order = 6)
    private Integer guests;

    @ExcelColumn(header = "Tổng tiền", order = 7)
    private BigDecimal totalPrice;

    @ExcelColumn(header = "Trạng thái", order = 8)
    private String status;
}
