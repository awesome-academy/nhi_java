package demo.tripgo.excel;

// Giữ kèm số dòng trong file để báo lỗi chỉ đúng chỗ cho người dùng sửa.
public record ExcelRow<T>(int rowNumber, T value) {
}
