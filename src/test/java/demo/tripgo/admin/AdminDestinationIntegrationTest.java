package demo.tripgo.admin;

import demo.tripgo.entity.Category;
import demo.tripgo.entity.Destination;
import demo.tripgo.entity.Tour;
import demo.tripgo.repository.BookingRepository;
import demo.tripgo.repository.CategoryRepository;
import demo.tripgo.repository.DepartureRepository;
import demo.tripgo.repository.DestinationRepository;
import demo.tripgo.repository.TourRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminDestinationIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired DestinationRepository destinations;
    @Autowired TourRepository tours;
    @Autowired CategoryRepository categories;
    @Autowired BookingRepository bookings;
    @Autowired DepartureRepository departures;

    @BeforeEach
    void setUp() {
        // Dọn theo đúng thứ tự khoá ngoại: lớp test chạy trước có thể để lại đơn/ngày khởi hành
        // trỏ vào tours, xoá tours trước sẽ vi phạm ràng buộc.
        bookings.deleteAll();
        departures.deleteAll();
        tours.deleteAll();
        destinations.deleteAll();
    }

    // ---- Danh sách ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void listsDestinationsWithTourCount() throws Exception {
        Destination daNang = saveDestination("Đà Nẵng", "da-nang");
        saveTour(daNang);
        saveTour(daNang);
        saveDestination("Sapa", "sapa");

        mvc.perform(get("/admin/destinations"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/destinations/list"))
            .andExpect(model().attribute("total", 2L))
            .andExpect(content().string(containsString("da-nang")))
            .andExpect(content().string(containsString("sapa")));
    }

    // Điểm đến chưa có tour vẫn phải xuất hiện (left join, không bị thành inner join).
    @Test
    @WithMockUser(roles = "ADMIN")
    void destinationWithoutToursStillAppears() throws Exception {
        saveDestination("Sapa", "sapa");

        mvc.perform(get("/admin/destinations"))
            .andExpect(model().attribute("total", 1L))
            .andExpect(content().string(containsString("sapa")));
    }

    // Tour đã xoá mềm không được tính vào số tour của điểm đến.
    @Test
    @WithMockUser(roles = "ADMIN")
    void deletedTourIsNotCounted() throws Exception {
        Destination daNang = saveDestination("Đà Nẵng", "da-nang");
        saveTour(daNang);
        Tour gone = saveTour(daNang);
        gone.setDeletedAt(LocalDateTime.now());
        tours.saveAndFlush(gone);

        mvc.perform(get("/admin/destinations"))
            .andExpect(content().string(containsString("da-nang")));

        assertThat(tours.countByDestinationIdAndDeletedAtIsNull(daNang.getId())).isEqualTo(1);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void searchFiltersByName() throws Exception {
        saveDestination("Đà Nẵng", "da-nang");
        saveDestination("Sapa", "sapa");

        mvc.perform(get("/admin/destinations").param("q", "sapa"))
            .andExpect(model().attribute("total", 1L))
            .andExpect(content().string(not(containsString("da-nang"))));
    }

    // ---- Tạo / sửa ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void createsDestinationAndGeneratesSlug() throws Exception {
        mvc.perform(post("/admin/destinations").with(csrf()).param("name", "Đà Nẵng"))
            .andExpect(redirectedUrl("/admin/destinations"));

        Destination saved = destinations.findAll().getFirst();
        assertThat(saved.getName()).isEqualTo("Đà Nẵng");
        assertThat(saved.getSlug()).isEqualTo("da-nang");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void duplicateSlugGetsSuffix() throws Exception {
        saveDestination("Đà Nẵng", "da-nang");

        mvc.perform(post("/admin/destinations").with(csrf()).param("name", "Đà Nẵng"));

        assertThat(destinations.findAll()).extracting(Destination::getSlug)
            .containsExactlyInAnyOrder("da-nang", "da-nang-2");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void emptyNameShowsFieldError() throws Exception {
        mvc.perform(post("/admin/destinations").with(csrf()).param("name", ""))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/destinations/form"))
            .andExpect(model().attributeHasFieldErrors("form", "name"))
            .andExpect(content().string(containsString("Tên điểm đến không được để trống")));

        assertThat(destinations.count()).isZero();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void editFormIsPrefilled() throws Exception {
        Destination daNang = saveDestination("Đà Nẵng", "da-nang");

        mvc.perform(get("/admin/destinations/{id}/edit", daNang.getId()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("form", hasProperty("name", is("Đà Nẵng"))))
            .andExpect(model().attribute("form", hasProperty("slug", is("da-nang"))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateChangesNameAndAppearsInPublicApi() throws Exception {
        Destination daNang = saveDestination("Đà Nẵng", "da-nang");
        saveTour(daNang);

        mvc.perform(post("/admin/destinations/{id}", daNang.getId()).with(csrf())
                .param("name", "Đà Nẵng City")
                .param("slug", "da-nang"))
            .andExpect(redirectedUrl("/admin/destinations"));

        mvc.perform(get("/api/v1/destinations"))
            .andExpect(content().string(containsString("Đà Nẵng City")));
    }

    // ---- Xoá: khác tour, điểm đến còn tour thì KHÔNG cho xoá ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void cannotDeleteDestinationThatStillHasTours() throws Exception {
        Destination daNang = saveDestination("Đà Nẵng", "da-nang");
        saveTour(daNang);

        mvc.perform(post("/admin/destinations/{id}/delete", daNang.getId()).with(csrf()))
            .andExpect(flash().attribute("flashType", "error"))
            .andExpect(flash().attribute("flashMessage", containsString("còn 1 tour")));

        assertThat(destinations.findActiveById(daNang.getId())).isPresent();
    }

    // Tour đã xoá mềm không tính là "còn tour" -> vẫn xoá được điểm đến.
    @Test
    @WithMockUser(roles = "ADMIN")
    void canDeleteWhenRemainingToursAreSoftDeleted() throws Exception {
        Destination daNang = saveDestination("Đà Nẵng", "da-nang");
        Tour gone = saveTour(daNang);
        gone.setDeletedAt(LocalDateTime.now());
        tours.saveAndFlush(gone);

        mvc.perform(post("/admin/destinations/{id}/delete", daNang.getId()).with(csrf()))
            .andExpect(flash().attribute("flashType", "success"));

        assertThat(destinations.findActiveById(daNang.getId())).isEmpty();
        assertThat(destinations.findById(daNang.getId())).isPresent();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteFreesSlugAndRestoreReclaimsIt() throws Exception {
        Destination sapa = saveDestination("Sapa", "sapa");

        mvc.perform(post("/admin/destinations/{id}/delete", sapa.getId()).with(csrf()));
        assertThat(destinations.findById(sapa.getId()).orElseThrow().getSlug())
            .isEqualTo("sapa-deleted-" + sapa.getId());

        mvc.perform(post("/admin/destinations/{id}/restore", sapa.getId()).with(csrf()))
            .andExpect(redirectedUrl("/admin/destinations/trash"));

        Destination restored = destinations.findById(sapa.getId()).orElseThrow();
        assertThat(restored.getSlug()).isEqualTo("sapa");
        assertThat(restored.isDeleted()).isFalse();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deletedDestinationDisappearsFromPublicApi() throws Exception {
        Destination sapa = saveDestination("Sapa", "sapa");

        mvc.perform(get("/api/v1/destinations"))
            .andExpect(content().string(containsString("sapa")));

        mvc.perform(post("/admin/destinations/{id}/delete", sapa.getId()).with(csrf()));

        mvc.perform(get("/api/v1/destinations"))
            .andExpect(content().string(not(containsString("\"sapa\""))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void trashListsOnlyDeleted() throws Exception {
        saveDestination("Đà Nẵng", "da-nang");
        Destination sapa = saveDestination("Sapa", "sapa");
        mvc.perform(post("/admin/destinations/{id}/delete", sapa.getId()).with(csrf()));

        mvc.perform(get("/admin/destinations/trash"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Sapa")))
            .andExpect(content().string(not(containsString("da-nang"))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void emptyTrashSaysSo() throws Exception {
        mvc.perform(get("/admin/destinations/trash"))
            .andExpect(content().string(containsString("Thùng rác trống")));
    }

    // ---- Bảo vệ ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteWithoutCsrfIsRejected() throws Exception {
        Destination sapa = saveDestination("Sapa", "sapa");

        mvc.perform(post("/admin/destinations/{id}/delete", sapa.getId()))
            .andExpect(status().isForbidden());

        assertThat(destinations.findActiveById(sapa.getId())).isPresent();
    }

    @Test
    @WithMockUser(roles = "USER")
    void normalUserIsDenied() throws Exception {
        mvc.perform(get("/admin/destinations"))
            .andExpect(redirectedUrl("/admin/login?denied"));
    }

    private Destination saveDestination(String name, String slug) {
        Destination destination = new Destination();
        destination.setName(name);
        destination.setSlug(slug);
        return destinations.save(destination);
    }

    private Tour saveTour(Destination destination) {
        Category category = categories.findBySlug("beach").orElseGet(() -> {
            Category c = new Category();
            c.setSlug("beach");
            c.setName("Biển đảo");
            return categories.save(c);
        });

        Tour tour = new Tour();
        tour.setTitle("Tour " + UUID.randomUUID().toString().substring(0, 4));
        tour.setSlug(UUID.randomUUID().toString());
        tour.setDestination(destination);
        tour.setCategory(category);
        tour.setDurationDays(3);
        tour.setPrice(new BigDecimal("1000000"));
        tour.setMaxGuests(20);
        return tours.save(tour);
    }
}
