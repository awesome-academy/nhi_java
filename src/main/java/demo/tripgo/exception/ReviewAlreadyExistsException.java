package demo.tripgo.exception;

// Ném khi user cố đánh giá một tour đã từng đánh giá; ánh xạ sang 409.
public class ReviewAlreadyExistsException extends RuntimeException {

    public ReviewAlreadyExistsException(Long tourId) {
        super("You have already reviewed tour " + tourId);
    }
}
