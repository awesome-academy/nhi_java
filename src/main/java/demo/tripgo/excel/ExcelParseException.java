package demo.tripgo.excel;

// Lỗi ở mức cả file: không phải file Excel, thiếu cột bắt buộc, quá nhiều dòng...
public class ExcelParseException extends RuntimeException {

    public ExcelParseException(String message) {
        super(message);
    }

    public ExcelParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
