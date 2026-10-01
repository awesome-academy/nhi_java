package demo.tripgo.excel;

import java.util.List;

// Kết quả đọc file: dòng đọc được và dòng lỗi nằm cạnh nhau. Nhờ vậy tầng trên nhập được phần
// hợp lệ và vẫn báo chính xác dòng nào sai — thay vì từ chối cả file vì một ô gõ nhầm.
public record ExcelReadResult<T>(List<ExcelRow<T>> rows, List<ExcelRowError> errors) {

    public int totalRows() {
        return rows.size() + errors.size();
    }

    public List<T> values() {
        return rows.stream().map(ExcelRow::value).toList();
    }
}
