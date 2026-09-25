package demo.tripgo.admin.excel;

import demo.tripgo.excel.ExcelColumn;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// Báo cáo doanh thu tháng, gộp theo tour.
@Getter
@Setter
public class RevenueExportRow {

    @ExcelColumn(header = "Tour", order = 1)
    private String tourTitle;

    @ExcelColumn(header = "Số đơn", order = 2)
    private Long bookingCount;

    @ExcelColumn(header = "Số khách", order = 3)
    private Long guestCount;

    @ExcelColumn(header = "Doanh thu", order = 4)
    private BigDecimal revenue;
}
