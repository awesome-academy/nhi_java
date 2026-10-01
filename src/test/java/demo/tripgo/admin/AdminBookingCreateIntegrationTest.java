package demo.tripgo.admin;

import demo.tripgo.entity.Booking;
import demo.tripgo.entity.BookingStatus;
import demo.tripgo.entity.Category;
import demo.tripgo.entity.Departure;
import demo.tripgo.entity.Destination;
import demo.tripgo.entity.Role;
import demo.tripgo.entity.Tour;
import demo.tripgo.entity.User;
import demo.tripgo.entity.UserStatus;
import demo.tripgo.repository.BookingRepository;
import demo.tripgo.repository.CategoryRepository;
import demo.tripgo.repository.DepartureRepository;
import demo.tripgo.repository.DestinationRepository;
import demo.tripgo.repository.TourRepository;
import demo.tripgo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// Admin tạo đơn hộ khách: đơn gắn vào tài khoản khách có sẵn, trạng thái Chờ xác nhận, và phải
// qua đúng luật trừ chỗ / tính tiền của luồng khách tự đặt.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminBookingCreateIntegrationTest {

    private static final String CUSTOMER_EMAIL = "khach@example.com";

    @Autowired MockMvc mvc;
    @Autowired BookingRepository bookings;
    @Autowired DepartureRepository departures;
    @Autowired TourRepository tours;
    @Autowired DestinationRepository destinations;
    @Autowired CategoryRepository categories;
    @Autowired UserRepository users;
    @Autowired demo.tripgo.TestDataCleaner cleaner;

    private Tour tour;
    private Departure departure;
    private User customer;

    @BeforeEach
    void setUp() {
        cleaner.clean();

        Destination destination = new Destination();
        destination.setName("Đà Nẵng");
        destination.setSlug("da-nang");
        destination = destinations.save(destination);

        Category category = new Category();
        category.setSlug("beach");
        category.setName("Biển đảo");
        category = categories.save(category);

        tour = new Tour();
        tour.setTitle("Đà Nẵng 3N2Đ");
        tour.setSlug("da-nang-3n2d");
        tour.setDestination(destination);
        tour.setCategory(category);
        tour.setDurationDays(3);
        tour.setPrice(new BigDecimal("4500000"));
        tour.setMaxGuests(20);
        tour = tours.save(tour);

        departure = saveDeparture(LocalDate.now().plusDays(30), 10, 0);

        customer = new User();
        customer.setEmail(CUSTOMER_EMAIL);
        customer.setFullName("Nguyễn Văn Khách");
        customer.setPassword("x");
        customer.setRole(Role.USER);
        customer.setStatus(UserStatus.ACTIVE);
        customer = users.save(customer);
    }

    // ---- Trang form ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void listPageLinksToCreateForm() throws Exception {
        mvc.perform(get("/admin/bookings"))
            .andExpect(content().string(containsString("/admin/bookings/new")));
    }

    // Chưa chọn tour: chỉ có bước chọn tour, chưa hiện form nhập đơn.
    @Test
    @WithMockUser(roles = "ADMIN")
    void formStartsWithTourPicker() throws Exception {
        mvc.perform(get("/admin/bookings/new"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/bookings/form"))
            .andExpect(content().string(containsString("Đà Nẵng 3N2Đ")))
            .andExpect(content().string(not(containsString("Email tài khoản khách"))));
    }

    // Chỉ ngày từ hôm nay trở đi VÀ còn chỗ: ngày đã qua và ngày hết chỗ bị bỏ khỏi danh sách.
    @Test
    @WithMockUser(roles = "ADMIN")
    void listsOnlyUpcomingDeparturesWithSeatsLeft() throws Exception {
        saveDeparture(LocalDate.now().minusDays(1), 10, 0);
        saveDeparture(LocalDate.now().plusDays(40), 10, 10);

        mvc.perform(get("/admin/bookings/new").param("tourId", tour.getId().toString()))
            .andExpect(model().attribute("departures", hasSize(1)))
            .andExpect(content().string(containsString("còn 10 chỗ")))
            .andExpect(content().string(containsString("Email tài khoản khách")));
    }

    // Ô chọn khách chỉ có khách (role USER) đang hoạt động: không có admin, không có tài khoản khoá.
    @Test
    @WithMockUser(roles = "ADMIN")
    void customerDropdownListsOnlyActiveCustomers() throws Exception {
        saveAccount("bi-khoa@example.com", Role.USER, UserStatus.BLOCKED);
        saveAccount("admin@tripgo.local", Role.ADMIN, UserStatus.ACTIVE);

        mvc.perform(get("/admin/bookings/new").param("tourId", tour.getId().toString()))
            .andExpect(model().attribute("customers", hasSize(1)))
            .andExpect(content().string(containsString("khach@example.com — Nguyễn Văn Khách")))
            .andExpect(content().string(not(containsString("bi-khoa@example.com"))))
            .andExpect(content().string(not(containsString("admin@tripgo.local"))));
    }

    // Giá trị <select> sửa tay được: gửi thẳng email admin lên vẫn phải bị chặn ở server.
    @Test
    @WithMockUser(roles = "ADMIN")
    void adminAccountCannotBeBookedFor() throws Exception {
        saveAccount("admin@tripgo.local", Role.ADMIN, UserStatus.ACTIVE);

        mvc.perform(validBooking("customerEmail", "admin@tripgo.local"))
            .andExpect(view().name("admin/bookings/form"))
            .andExpect(content().string(containsString("không phải tài khoản khách")));

        assertThat(bookings.count()).isZero();
    }

    // Tour đã vào thùng rác (hoặc id sửa tay) -> không hiện form, báo chọn tour khác.
    @Test
    @WithMockUser(roles = "ADMIN")
    void trashedTourCannotBeBooked() throws Exception {
        tour.setDeletedAt(LocalDateTime.now());
        tours.save(tour);

        mvc.perform(get("/admin/bookings/new").param("tourId", tour.getId().toString()))
            .andExpect(model().attribute("selectedTour", nullValue()))
            .andExpect(content().string(containsString("Tour này không còn bán")));
    }

    // ---- Tạo đơn ----

    // Email khách gõ hoa vẫn khớp tài khoản lưu chữ thường. Tiền do server tính:
    // 3 khách x 4.500.000 = 13.500.000.
    @Test
    @WithMockUser(roles = "ADMIN")
    void createsPendingBookingForExistingCustomer() throws Exception {
        mvc.perform(validBooking("customerEmail", "Khach@Example.com"))
            .andExpect(redirectedUrl("/admin/bookings"))
            .andExpect(flash().attribute("flashType", "success"))
            .andExpect(flash().attribute("flashMessage", containsString("Đã tạo đơn TG-")));

        List<Booking> saved = bookings.findAll();
        assertThat(saved).hasSize(1);
        Booking booking = bookings.findWithDetailsById(saved.getFirst().getId()).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.getUser().getId()).isEqualTo(customer.getId());
        assertThat(booking.getTotalPrice()).isEqualByComparingTo("13500000");
        assertThat(booking.getContact().getPhone()).isEqualTo("0901234567");
        assertThat(seatsBooked()).isEqualTo(3);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unknownCustomerIsReportedOnForm() throws Exception {
        mvc.perform(validBooking("customerEmail", "khong-co@example.com"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/bookings/form"))
            .andExpect(content().string(containsString("Không có tài khoản khách nào với email khong-co@example.com")));

        assertThat(bookings.count()).isZero();
        assertThat(seatsBooked()).isZero();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void blockedCustomerCannotBeBookedFor() throws Exception {
        customer.setStatus(UserStatus.BLOCKED);
        users.save(customer);

        mvc.perform(validBooking())
            .andExpect(content().string(containsString("đang không hoạt động")));

        assertThat(bookings.count()).isZero();
    }

    // Không đủ chỗ: báo trên form và KHÔNG trừ chỗ nào.
    @Test
    @WithMockUser(roles = "ADMIN")
    void notEnoughSeatsKeepsSeatsUntouched() throws Exception {
        mvc.perform(validBooking("adults", "11"))
            .andExpect(view().name("admin/bookings/form"))
            .andExpect(content().string(containsString("Chỉ còn 10 chỗ")));

        assertThat(bookings.count()).isZero();
        assertThat(seatsBooked()).isZero();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void dateWithoutDepartureIsRejected() throws Exception {
        mvc.perform(validBooking("departureDate", LocalDate.now().plusDays(31).toString()))
            .andExpect(content().string(containsString("Tour không có chuyến khởi hành vào ngày")));

        assertThat(bookings.count()).isZero();
    }

    // Lỗi nhập liệu hiện đúng dưới từng ô, và danh sách ngày khởi hành vẫn còn để sửa tiếp.
    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidInputShowsFieldErrors() throws Exception {
        mvc.perform(validBooking("adults", "0", "contactPhone", "12345"))
            .andExpect(view().name("admin/bookings/form"))
            .andExpect(model().attributeHasFieldErrors("form", "adults", "contactPhone"))
            .andExpect(model().attribute("departures", hasSize(1)))
            .andExpect(content().string(containsString("Số điện thoại không hợp lệ")))
            // Ngày đã chọn phải còn được chọn sau khi báo lỗi, không bắt admin chọn lại.
            .andExpect(content().string(containsString(
                "value=\"" + departure.getDepartureDate() + "\" selected=\"selected\"")))
            .andExpect(content().string(containsString(
                "value=\"" + CUSTOMER_EMAIL + "\" selected=\"selected\"")));

        assertThat(bookings.count()).isZero();
    }

    // ---- Bảo vệ ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void createWithoutCsrfIsRejected() throws Exception {
        mvc.perform(bookingParams(post("/admin/bookings")))
            .andExpect(status().isForbidden());

        assertThat(bookings.count()).isZero();
    }

    @Test
    @WithMockUser(roles = "USER")
    void normalUserCannotOpenCreateForm() throws Exception {
        mvc.perform(get("/admin/bookings/new"))
            .andExpect(redirectedUrl("/admin/login?denied"));
    }

    // ---- Tiện ích ----

    // 2 người lớn + 1 trẻ em vào ngày khởi hành của setUp. overrides là cặp (tên, giá trị) THAY
    // cho giá trị mặc định: .param() của MockMvc nối thêm giá trị chứ không ghi đè, gọi hai lần
    // cùng tên là gửi lên hai giá trị.
    private MockHttpServletRequestBuilder validBooking(String... overrides) {
        return bookingParams(post("/admin/bookings"), overrides).with(csrf());
    }

    private MockHttpServletRequestBuilder bookingParams(
        MockHttpServletRequestBuilder request, String... overrides
    ) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("tourId", tour.getId().toString());
        params.put("departureDate", departure.getDepartureDate().toString());
        params.put("customerEmail", CUSTOMER_EMAIL);
        params.put("adults", "2");
        params.put("children", "1");
        params.put("contactName", "Nguyễn Văn Khách");
        params.put("contactEmail", CUSTOMER_EMAIL);
        params.put("contactPhone", "0901234567");
        params.put("note", "Đặt qua điện thoại");
        for (int i = 0; i < overrides.length; i += 2) {
            params.put(overrides[i], overrides[i + 1]);
        }
        params.forEach(request::param);
        return request;
    }

    private void saveAccount(String email, Role role, UserStatus status) {
        User user = new User();
        user.setEmail(email);
        user.setFullName("Tài khoản " + email);
        user.setPassword("x");
        user.setRole(role);
        user.setStatus(status);
        users.save(user);
    }

    private int seatsBooked() {
        return departures.findById(departure.getId()).orElseThrow().getBookedSeats();
    }

    private Departure saveDeparture(LocalDate date, int totalSeats, int bookedSeats) {
        Departure d = new Departure();
        d.setTour(tour);
        d.setDepartureDate(date);
        d.setTotalSeats(totalSeats);
        d.setBookedSeats(bookedSeats);
        return departures.save(d);
    }
}
