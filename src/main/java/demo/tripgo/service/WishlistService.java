package demo.tripgo.service;

import demo.tripgo.dto.response.RemoveWishlistResponse;
import demo.tripgo.dto.response.WishlistResponse;
import demo.tripgo.entity.Tour;
import demo.tripgo.entity.User;
import demo.tripgo.exception.ResourceNotFoundException;
import demo.tripgo.repository.TourRepository;
import demo.tripgo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

// Wishlist của riêng từng user: mọi thao tác đều bắt đầu từ user lấy trong JWT,
// nên không có cách nào tác động sang wishlist của người khác (hợp đồng 6.6 / AC).
@Service
@Transactional(readOnly = true)
public class WishlistService {

    private final UserRepository userRepository;
    private final TourRepository tourRepository;

    public WishlistService(UserRepository userRepository, TourRepository tourRepository) {
        this.userRepository = userRepository;
        this.tourRepository = tourRepository;
    }

    public WishlistResponse getWishlist(User user) {
        return toResponse(loadWithWishlist(user));
    }

    @Transactional
    public WishlistResponse addTour(User user, Long tourId) {
        Tour tour = tourRepository.findActiveById(tourId)
            .orElseThrow(() -> new ResourceNotFoundException("tour"));
        // Set.add trả false nếu đã có -> thêm trùng không sinh bản ghi lặp, cũng không báo lỗi
        // (thao tác idempotent, bấm tim hai lần vẫn chỉ là "đã thích").
        User managed = loadWithWishlist(user);
        managed.getWishlist().add(tour);
        return toResponse(managed);
    }

    @Transactional
    public RemoveWishlistResponse removeTour(User user, Long tourId) {
        User managed = loadWithWishlist(user);
        // Xoá tour không có trong wishlist vẫn là 200: DELETE nên idempotent, gọi lại không lỗi.
        // Nhưng cờ removed cho client phân biệt "vừa xoá xong" với "vốn đã không có trong list".
        boolean removed = managed.getWishlist().removeIf(tour -> tour.getId().equals(tourId));
        return new RemoveWishlistResponse(
            removed ? "Xoá tour khỏi wishlist thành công" : "Tour không có trong wishlist",
            removed,
            tourId
        );
    }

    // Principal từ JWT đã detached nên phải nạp lại bản managed kèm collection.
    private User loadWithWishlist(User user) {
        return userRepository.findByIdWithWishlist(user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("người dùng"));
    }

    // Sắp theo id để thứ tự ổn định giữa các lần gọi (Set không đảm bảo thứ tự sau khi reload).
    // Tour bị xoá mềm sau khi user đã lưu thì biến khỏi wishlist, nhưng hàng trong user_wishlist
    // vẫn còn: khôi phục tour từ thùng rác là nó hiện lại đúng chỗ cũ.
    private WishlistResponse toResponse(User user) {
        List<Long> tourIds = user.getWishlist().stream()
            .filter(tour -> !tour.isDeleted())
            .map(Tour::getId)
            .sorted(Comparator.naturalOrder())
            .toList();
        return new WishlistResponse(tourIds);
    }
}
