package demo.tripgo.controller;

import demo.tripgo.entity.Destination;
import demo.tripgo.entity.Tour;
import demo.tripgo.entity.TourCategory;
import demo.tripgo.repository.DestinationRepository;
import demo.tripgo.repository.TourRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DestinationControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TourRepository tours;
    @Autowired DestinationRepository destinations;

    @BeforeEach
    void setUp() {
        tours.deleteAll();
        destinations.deleteAll();
    }

    private Destination saveDestination(String name, String slug) {
        Destination d = new Destination();
        d.setName(name);
        d.setSlug(slug);
        return destinations.save(d);
    }

    private void saveTour(String title, Destination destination) {
        Tour tour = new Tour();
        tour.setTitle(title);
        tour.setDestination(destination);
        tour.setCategory(TourCategory.BEACH);
        tour.setDurationDays(3);
        tour.setPrice(new BigDecimal("1000000"));
        tour.setMaxGuests(20);
        tours.save(tour);
    }

    @Test
    void listReturnsDestinationsSortedByNameWithTourCount() throws Exception {
        Destination daNang = saveDestination("Da Nang", "da-nang");
        Destination haNoi = saveDestination("Ha Noi", "ha-noi");
        saveDestination("An Empty Place", "empty"); // 0 tour

        saveTour("DN 1", daNang);
        saveTour("DN 2", daNang);
        saveTour("HN 1", haNoi);

        mvc.perform(get("/api/v1/destinations").contextPath("/api/v1").servletPath("/destinations"))
            .andExpect(status().isOk())
            // Object envelope, không phải array trần.
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(3))
            // Sắp theo name: "An Empty Place" < "Da Nang" < "Ha Noi".
            .andExpect(jsonPath("$.data[0].slug").value("empty"))
            .andExpect(jsonPath("$.data[0].tourCount").value(0))
            .andExpect(jsonPath("$.data[1].slug").value("da-nang"))
            .andExpect(jsonPath("$.data[1].tourCount").value(2))
            .andExpect(jsonPath("$.data[2].slug").value("ha-noi"))
            .andExpect(jsonPath("$.data[2].tourCount").value(1));
    }

    @Test
    void listWithNoDestinationsReturnsEmptyData() throws Exception {
        mvc.perform(get("/api/v1/destinations").contextPath("/api/v1").servletPath("/destinations"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void listIsPublicAndDoesNotRequireAuth() throws Exception {
        saveDestination("Da Nang", "da-nang");
        // Không gửi token vẫn 200 (không phải 401).
        mvc.perform(get("/api/v1/destinations").contextPath("/api/v1").servletPath("/destinations"))
            .andExpect(status().isOk());
    }
}
