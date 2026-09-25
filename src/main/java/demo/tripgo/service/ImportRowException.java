package demo.tripgo.service;

// Lỗi nghiệp vụ ở một dòng của file nhập (điểm đến không tồn tại, giá âm...).
// Bắt ngay trong vòng lặp nên những dòng khác vẫn nhập bình thường.
public class ImportRowException extends RuntimeException {

    public ImportRowException(String message) {
        super(message);
    }
}
