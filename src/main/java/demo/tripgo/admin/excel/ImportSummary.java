package demo.tripgo.admin.excel;

import demo.tripgo.excel.ExcelRowError;

import java.util.List;

// Kết quả nhập file, hiển thị lại cho admin.
//
// Gộp hai loại lỗi vào một danh sách: lỗi định dạng ô (ExcelMapper phát hiện) và lỗi nghiệp vụ
// (điểm đến không tồn tại, slug đã dùng). Với người nhập thì cả hai đều là "dòng 7 có vấn đề",
// không cần phân biệt tầng nào sinh ra.
public record ImportSummary(
    int totalRows,
    int imported,
    int skipped,
    List<ExcelRowError> errors
) {
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
