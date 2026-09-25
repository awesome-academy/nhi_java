package demo.tripgo.admin;

import demo.tripgo.entity.Booking;
import demo.tripgo.entity.BookingStatus;
import demo.tripgo.entity.Category;
import demo.tripgo.entity.ContactInfo;
import demo.tripgo.entity.Departure;
import demo.tripgo.entity.Destination;
import demo.tripgo.entity.Role;
import demo.tripgo.entity.Tour;
import demo.tripgo.entity.User;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminTourDeleteRestoreIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TourRepository tours;
    @Autowired DestinationRepository destinations;
    @Autowired CategoryRepository categories;
    @Autowired BookingRepository bookings;
    @Autowired DepartureRepository departures;
    @Autowired UserRepository users;

    private Destination destination;
    private Tour tour;

    @BeforeEach
    void setUp() {
        bookings.deleteAll();
        departures.deleteAll();
        tours.deleteAll();
        destinations.deleteAll();
        users.deleteAll();

        destination = new Destination();
        destination.setName("Đà Nẵng");
        destination.setSlug("da-nang");
        destination = destinations.save(destination);

        tour = saveTour("Đà Nẵng 3N2Đ", "da-nang-3n2d");
    }

    // ---- Xoá ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteMovesTourToTrashInsteadOfRemovingRow() throws Exception {
        mvc.perform(post("/admin/tours/{id}/delete", tour.getId()).with(csrf()))
            .andExpect(redirectedUrl("/admin/tours"))
            .andExpect(flash().attribute("flashMessage", containsString("vào thùng rác")));

        // Hàng vẫn còn — đây là điểm khác biệt với xoá cứng.
        assertThat(tours.findById(tour.getId())).isPresent();
        assertThat(tours.findActiveById(tour.getId())).isEmpty();
        assertThat(tours.count()).isEqualTo(1);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deletedTourVanishesFromListAndPublicApi() throws Exception {
        mvc.perform(post("/admin/tours/{id}/delete", tour.getId()).with(csrf()));

        mvc.perform(get("/admin/tours"))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Đà Nẵng 3N2Đ"))));
        mvc.perform(get("/api/v1/tours"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Đà Nẵng 3N2Đ"))));
    }

    // Không chặn xoá tour đã có đơn, nhưng phải nói cho admin biết.
    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteWarnsWhenTourStillHasActiveBookings() throws Exception {
        saveBooking(BookingStatus.CONFIRMED);
        saveBooking(BookingStatus.CANCELLED);

        mvc.perform(post("/admin/tours/{id}/delete", tour.getId()).with(csrf()))
            .andExpect(flash().attribute("flashMessage", containsString("còn 1 đơn đặt còn hiệu lực")));
    }

    // Đơn cũ vẫn tra được tour sau khi xoá mềm — lý do chính chọn xoá mềm thay vì cascade.
    @Test
    @WithMockUser(roles = "ADMIN")
    void existingBookingStillResolvesItsTour() throws Exception {
        Booking booking = saveBooking(BookingStatus.CONFIRMED);
        // getId() trên proxy lazy không cần nạp entity, nên đọc được ngoài transaction.
        Long userId = booking.getUser().getId();

        mvc.perform(post("/admin/tours/{id}/delete", tour.getId()).with(csrf()));

        // findByIdAndUserId có @EntityGraph nạp sẵn tour -> đọc được sau khi session đóng.
        Booking reloaded = bookings.findByIdAndUserId(booking.getId(), userId).orElseThrow();
        assertThat(reloaded.getTour().getTitle()).isEqualTo("Đà Nẵng 3N2Đ");
        assertThat(reloaded.getTour().isDeleted()).isTrue();
    }

    // ---- Slug ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteFreesSlugSoANewTourCanReuseIt() throws Exception {
        mvc.perform(post("/admin/tours/{id}/delete", tour.getId()).with(csrf()));

        assertThat(tours.findById(tour.getId()).orElseThrow().getSlug())
            .isEqualTo("da-nang-3n2d-deleted-" + tour.getId());

        // Slug gốc giờ dùng lại được cho tour mới.
        Tour fresh = saveTour("Đà Nẵng bản mới", "da-nang-3n2d");
        assertThat(fresh.getId()).isNotNull();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void restoreReclaimsOriginalSlugWhenStillFree() throws Exception {
        mvc.perform(post("/admin/tours/{id}/delete", tour.getId()).with(csrf()));
        mvc.perform(post("/admin/tours/{id}/restore", tour.getId()).with(csrf()))
            .andExpect(redirectedUrl("/admin/tours/trash"));

        Tour restored = tours.findById(tour.getId()).orElseThrow();
        assertThat(restored.getSlug()).isEqualTo("da-nang-3n2d");
        assertThat(restored.isDeleted()).isFalse();
    }

    // Slug gốc đã bị tour khác chiếm trong lúc nằm thùng rác -> giữ hậu tố, không được ném lỗi.
    @Test
    @WithMockUser(roles = "ADMIN")
    void restoreKeepsSuffixWhenOriginalSlugWasTaken() throws Exception {
        mvc.perform(post("/admin/tours/{id}/delete", tour.getId()).with(csrf()));
        saveTour("Tour khác", "da-nang-3n2d");

        mvc.perform(post("/admin/tours/{id}/restore", tour.getId()).with(csrf()));

        Tour restored = tours.findById(tour.getId()).orElseThrow();
        assertThat(restored.getSlug()).isEqualTo("da-nang-3n2d-deleted-" + tour.getId());
        assertThat(restored.isDeleted()).isFalse();
    }

    // ---- Thùng rác ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void trashListsOnlyDeletedTours() throws Exception {
        saveTour("Tour còn bán", "tour-con-ban");
        mvc.perform(post("/admin/tours/{id}/delete", tour.getId()).with(csrf()));

        mvc.perform(get("/admin/tours/trash"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Đà Nẵng 3N2Đ")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Tour còn bán"))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void emptyTrashSaysSo() throws Exception {
        mvc.perform(get("/admin/tours/trash"))
            .andExpect(content().string(containsString("Thùng rác trống")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void restoringTwiceIsNotAnError() throws Exception {
        mvc.perform(post("/admin/tours/{id}/delete", tour.getId()).with(csrf()));
        mvc.perform(post("/admin/tours/{id}/restore", tour.getId()).with(csrf()));
        mvc.perform(post("/admin/tours/{id}/restore", tour.getId()).with(csrf()))
            .andExpect(redirectedUrl("/admin/tours/trash"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deletingUnknownTourReturns404() throws Exception {
        mvc.perform(post("/admin/tours/{id}/delete", 999999).with(csrf()))
            .andExpect(status().isNotFound());
    }

    // ---- Bảo vệ ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/admin/tours/{id}/delete", tour.getId()))
            .andExpect(status().isForbidden());
        assertThat(tours.findActiveById(tour.getId())).isPresent();
    }

    @Test
    @WithMockUser(roles = "USER")
    void normalUserCannotDelete() throws Exception {
        mvc.perform(post("/admin/tours/{id}/delete", tour.getId()).with(csrf()))
            .andExpect(redirectedUrl("/admin/login?denied"));
        assertThat(tours.findActiveById(tour.getId())).isPresent();
    }

    private Tour saveTour(String title, String slug) {
        Tour t = new Tour();
        t.setTitle(title);
        t.setSlug(slug);
        t.setDestination(destination);
        t.setCategory(category());
        t.setDurationDays(3);
        t.setPrice(new BigDecimal("4500000"));
        t.setMaxGuests(20);
        t.setDescription("mô tả");
        return tours.save(t);
    }

    private Category category() {
        return categories.findBySlug("beach").orElseGet(() -> {
            Category c = new Category();
            c.setSlug("beach");
            c.setName("Biển đảo");
            return categories.save(c);
        });
    }

    private Booking saveBooking(BookingStatus status) {
        User user = new User();
        user.setFullName("Khách");
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPassword("x");
        user.setRole(Role.USER);
        user = users.save(user);

        // Booking trỏ tới Departure (ngày khởi hành) chứ không giữ ngày trực tiếp.
        Departure departure = new Departure();
        departure.setTour(tour);
        departure.setDepartureDate(LocalDate.now().plusDays(30));
        departure.setTotalSeats(20);
        departure.setBookedSeats(2);
        departure = departures.save(departure);

        ContactInfo contact = new ContactInfo();
        contact.setFullName("Khách");
        contact.setEmail("k@example.com");
        contact.setPhone("0900000000");

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setTour(tour);
        booking.setDeparture(departure);
        booking.setCode(UUID.randomUUID().toString().substring(0, 8));
        booking.setAdults(2);
        booking.setChildren(0);
        booking.setTotalPrice(new BigDecimal("9000000"));
        booking.setStatus(status);
        booking.setContact(contact);
        return bookings.save(booking);
    }
}
