package demo.tripgo.excel;

// Lỗi ở một ô cụ thể. Bắt ở mức dòng để một ô sai chỉ làm hỏng dòng đó, không hỏng cả file.
public class ExcelCellException extends RuntimeException {

    public ExcelCellException(String message) {
        super(message);
    }
}
