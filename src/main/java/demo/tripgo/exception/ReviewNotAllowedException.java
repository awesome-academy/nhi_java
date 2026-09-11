package demo.tripgo.exception;

// Ném khi user đánh giá một tour mình chưa từng đặt; ánh xạ sang 403.
// Dùng 403 (không phải 404) vì tour có tồn tại — vấn đề là người gọi chưa đủ điều kiện.
public class ReviewNotAllowedException extends RuntimeException {

    public ReviewNotAllowedException() {
        super("Bạn cần đặt tour này trước khi đánh giá");
    }
}
