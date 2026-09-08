package demo.tripgo.controller;

import demo.tripgo.entity.Destination;
import demo.tripgo.entity.ItineraryDay;
import demo.tripgo.entity.Tour;
import demo.tripgo.entity.TourCategory;
import demo.tripgo.entity.TourImage;
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
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TourControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TourRepository tours;
    @Autowired DestinationRepository destinations;

    private Destination daNang;
    private Destination haNoi;

    @BeforeEach
    void setUp() {
        tours.deleteAll();
        destinations.deleteAll();
        daNang = saveDestination("Da Nang", "da-nang");
        haNoi = saveDestination("Ha Noi", "ha-noi");
    }

    private Destination saveDestination(String name, String slug) {
        Destination d = new Destination();
        d.setName(name);
        d.setSlug(slug);
        return destinations.save(d);
    }

    private Tour saveTour(String title, Destination destination, TourCategory category,
                          int durationDays, String price, double rating) {
        Tour tour = new Tour();
        tour.setTitle(title);
        tour.setDestination(destination);
        tour.setCategory(category);
        tour.setDurationDays(durationDays);
        tour.setPrice(new BigDecimal(price));
        tour.setRatingAvg(rating);
        tour.setReviewCount((int) (rating * 10));
        tour.setMaxGuests(20);
        tour.setThumbnailUrl("https://img/" + title + ".jpg");
        tour.setDescription("Description of " + title);
        return tours.save(tour);
    }

    // ---- GET /tours: filtering + sorting + pagination ----

    @Test
    void filtersByDestinationSortsByPriceAscAndPaginates() throws Exception {
        saveTour("DN-cheap", daNang, TourCategory.BEACH, 3, "100", 4.0);
        saveTour("DN-mid", daNang, TourCategory.BEACH, 3, "200", 4.5);
        saveTour("DN-expensive", daNang, TourCategory.BEACH, 3, "300", 3.0);
        saveTour("HN-noise", haNoi, TourCategory.CITY, 2, "150", 5.0);

        // Trang 1, limit 2, sort giá tăng -> [DN-cheap, DN-mid].
        mvc.perform(get("/api/v1/tours").contextPath("/api/v1").servletPath("/tours")
                .param("destination", "da-nang").param("sort", "price_asc")
                .param("page", "1").param("limit", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].title").value("DN-cheap"))
            .andExpect(jsonPath("$.data[1].title").value("DN-mid"))
            .andExpect(jsonPath("$.data[0].destination.slug").value("da-nang"));

        // Trang 2 -> [DN-expensive], không lẫn tour Ha Noi.
        mvc.perform(get("/api/v1/tours").contextPath("/api/v1").servletPath("/tours")
                .param("destination", "da-nang").param("sort", "price_asc")
                .param("page", "2").param("limit", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.page").value(2))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].title").value("DN-expensive"));
    }

    @Test
    void sortByRatingDescReturnsHighestRatedFirst() throws Exception {
        saveTour("low", daNang, TourCategory.BEACH, 3, "100", 3.0);
        saveTour("high", daNang, TourCategory.BEACH, 3, "100", 4.9);
        saveTour("mid", daNang, TourCategory.BEACH, 3, "100", 4.0);

        mvc.perform(get("/api/v1/tours").contextPath("/api/v1").servletPath("/tours")
                .param("sort", "rating_desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].title").value("high"))
            .andExpect(jsonPath("$.data[1].title").value("mid"))
            .andExpect(jsonPath("$.data[2].title").value("low"));
    }

    @Test
    void combinesCategoryPriceDurationRatingAndKeywordFilters() throws Exception {
        saveTour("Beach getaway", daNang, TourCategory.BEACH, 3, "500", 4.5);
        saveTour("Beach budget", daNang, TourCategory.BEACH, 5, "150", 4.5);
        saveTour("Mountain trek", daNang, TourCategory.MOUNTAIN, 3, "500", 4.5);

        mvc.perform(get("/api/v1/tours").contextPath("/api/v1").servletPath("/tours")
                .param("q", "beach")
                .param("category", "beach")
                .param("minPrice", "300")
                .param("maxPrice", "800")
                .param("duration", "3")
                .param("rating", "4.0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.data[0].title").value("Beach getaway"));
    }

    @Test
    void emptyResultReturnsEmptyDataAndZeroTotal() throws Exception {
        saveTour("only-tour", daNang, TourCategory.BEACH, 3, "100", 4.0);

        mvc.perform(get("/api/v1/tours").contextPath("/api/v1").servletPath("/tours")
                .param("destination", "nowhere"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void invalidPaginationParamsReturn400() throws Exception {
        mvc.perform(get("/api/v1/tours").contextPath("/api/v1").servletPath("/tours")
                .param("page", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.errors.page").isNotEmpty());

        mvc.perform(get("/api/v1/tours").contextPath("/api/v1").servletPath("/tours")
                .param("limit", "100"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.errors.limit").isNotEmpty());
    }

    @Test
    void invalidSortAndCategoryReturn400() throws Exception {
        mvc.perform(get("/api/v1/tours").contextPath("/api/v1").servletPath("/tours")
                .param("sort", "cheapest"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));

        mvc.perform(get("/api/v1/tours").contextPath("/api/v1").servletPath("/tours")
                .param("category", "spaceship"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    // ---- GET /tours/{id}: detail ----

    @Test
    void detailReturnsFullTourForClientDetailScreen() throws Exception {
        Tour tour = new Tour();
        tour.setTitle("Full Da Nang Tour");
        tour.setDestination(daNang);
        tour.setCategory(TourCategory.BEACH);
        tour.setDurationDays(4);
        tour.setPrice(new BigDecimal("999.99"));
        tour.setDiscountPrice(new BigDecimal("799.99"));
        tour.setRatingAvg(4.8);
        tour.setReviewCount(120);
        tour.setMaxGuests(15);
        tour.setThumbnailUrl("https://img/thumb.jpg");
        tour.setDescription("A complete tour");
        tour.setHighlights(List.of("Sunrise", "Local food"));
        tour.setIncluded(List.of("Hotel", "Breakfast"));
        tour.setExcluded(List.of("Flights"));
        TourImage img1 = new TourImage();
        img1.setUrl("https://img/1.jpg");
        img1.setPosition(0);
        TourImage img2 = new TourImage();
        img2.setUrl("https://img/2.jpg");
        img2.setPosition(1);
        tour.addImage(img1);
        tour.addImage(img2);
        ItineraryDay d1 = new ItineraryDay();
        d1.setDayNumber(1);
        d1.setTitle("Arrival");
        d1.setDescription("Check in");
        ItineraryDay d2 = new ItineraryDay();
        d2.setDayNumber(2);
        d2.setTitle("Explore");
        d2.setDescription("City tour");
        tour.addItineraryDay(d1);
        tour.addItineraryDay(d2);
        Long id = tours.save(tour).getId();

        mvc.perform(get("/api/v1/tours/" + id).contextPath("/api/v1").servletPath("/tours/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.title").value("Full Da Nang Tour"))
            .andExpect(jsonPath("$.destination.slug").value("da-nang"))
            .andExpect(jsonPath("$.category").value("BEACH"))
            .andExpect(jsonPath("$.durationDays").value(4))
            .andExpect(jsonPath("$.maxGuests").value(15))
            .andExpect(jsonPath("$.reviewCount").value(120))
            .andExpect(jsonPath("$.highlights.length()").value(2))
            .andExpect(jsonPath("$.included[0]").value("Hotel"))
            .andExpect(jsonPath("$.excluded[0]").value("Flights"))
            .andExpect(jsonPath("$.images.length()").value(2))
            .andExpect(jsonPath("$.images[0].url").value("https://img/1.jpg"))
            .andExpect(jsonPath("$.itinerary.length()").value(2))
            .andExpect(jsonPath("$.itinerary[0].title").value("Arrival"))
            .andExpect(jsonPath("$.itinerary[1].dayNumber").value(2));
    }

    @Test
    void detailForUnknownIdReturns404() throws Exception {
        mvc.perform(get("/api/v1/tours/999999").contextPath("/api/v1").servletPath("/tours/999999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
