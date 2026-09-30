package demo.tripgo.service;

import demo.tripgo.admin.AdminUserRow;
import demo.tripgo.entity.Role;
import demo.tripgo.entity.User;
import demo.tripgo.exception.InvalidRequestParameterException;
import demo.tripgo.exception.ResourceNotFoundException;
import demo.tripgo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;

// Quản lý người dùng ở khu quản trị: xem danh sách và đổi role USER <-> ADMIN.
@Service
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);

    private final UserRepository userRepository;

    public UserAdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // role null = không lọc.
    @Transactional(readOnly = true)
    public Page<AdminUserRow> list(String keyword, Role role, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size,
            Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        EnumSet<Role> roles = role == null ? EnumSet.allOf(Role.class) : EnumSet.of(role);
        return userRepository.searchForAdmin(SearchText.likePatternKeepAccents(keyword), roles, pageable)
            .map(this::toRow);
    }

    // Trả về user sau khi đổi để controller báo lại email. Đổi sang đúng role đang có là no-op.
    //
    // Hai quy tắc khi HẠ quyền, đều để hệ thống không tự khoá mình ra ngoài:
    //  - không tự hạ quyền chính mình (mất quyền giữa chừng, không ai nâng lại được nếu là admin duy nhất);
    //  - không hạ admin cuối cùng.
    // Nâng quyền thì không có rủi ro đó nên không chặn gì.
    @Transactional
    public User changeRole(Long id, Role newRole, String currentAdminEmail) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("người dùng"));

        if (user.getRole() == newRole) {
            return user;
        }

        if (newRole == Role.USER) {
            if (user.getEmail().equalsIgnoreCase(currentAdminEmail)) {
                throw new InvalidRequestParameterException("Bạn không thể tự hạ quyền của chính mình");
            }
            List<User> admins = userRepository.lockByRole(Role.ADMIN);
            if (admins.size() <= 1) {
                throw new InvalidRequestParameterException("Hệ thống phải còn ít nhất một admin");
            }
        }

        user.setRole(newRole);
        log.info("{} đổi role của {} thành {}", currentAdminEmail, user.getEmail(), newRole);
        return user;
    }

    private AdminUserRow toRow(User user) {
        return new AdminUserRow(
            user.getId(),
            user.getFullName(),
            user.getEmail(),
            user.getProvider(),
            user.getRole(),
            user.getStatus(),
            user.getCreatedAt());
    }
}
