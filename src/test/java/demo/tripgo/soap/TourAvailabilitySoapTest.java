package demo.tripgo.soap;

import demo.tripgo.TestDataCleaner;
import demo.tripgo.entity.Category;
import demo.tripgo.entity.Departure;
import demo.tripgo.entity.Destination;
import demo.tripgo.entity.Tour;
import demo.tripgo.repository.CategoryRepository;
import demo.tripgo.repository.DepartureRepository;
import demo.tripgo.repository.DestinationRepository;
import demo.tripgo.repository.TourRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.xml.transform.StringSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.ws.test.server.RequestCreators.withPayload;
import static org.springframework.ws.test.server.ResponseMatchers.clientOrSenderFault;
import static org.springframework.ws.test.server.ResponseMatchers.xpath;

// Gửi SOAP request thật vào endpoint qua MockWebServiceClient — đi qua đúng lớp unmarshal/marshal
// mà client đối tác sẽ dùng, chứ không gọi thẳng method Java.
@SpringBootTest
@ActiveProfiles("test")
class TourAvailabilitySoapTest {

    private static final String NS = "http://tripgo.demo/soap";

    @Autowired TestDataCleaner cleaner;
    @Autowired TourRepository tours;
    @Autowired DestinationRepository destinations;
    @Autowired CategoryRepository categories;
    @Autowired DepartureRepository departures;
    @Autowired org.springframework.context.ApplicationContext context;

    private MockWebServiceClient client;
    private Tour tour;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        client = MockWebServiceClient.createClient(context);

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
        tour.setPrice(new BigDecimal("1000000"));
        tour.setMaxGuests(20);
        tour = tours.save(tour);

        saveDeparture(LocalDate.now().plusDays(10), 20, 5);
        saveDeparture(LocalDate.now().plusDays(40), 15, 15);
        // Ngày đã qua: không được trả về khi không truyền fromDate.
        saveDeparture(LocalDate.now().minusDays(5), 10, 0);
    }

    @Test
    void returnsUpcomingDeparturesWithRemainingSeats() {
        client.sendRequest(withPayload(request("da-nang-3n2d", null)))
            .andExpect(xpath("//tns:tourTitle", ns()).evaluatesTo("Đà Nẵng 3N2Đ"))
            // Chỉ 2 ngày sắp tới, ngày đã qua bị loại.
            .andExpect(xpath("count(//tns:departure)", ns()).evaluatesTo(2D))
            .andExpect(xpath("(//tns:departure)[1]/tns:remainingSeats", ns()).evaluatesTo(15D))
            // Chuyến đã đầy vẫn trả về, với 0 chỗ trống.
            .andExpect(xpath("(//tns:departure)[2]/tns:remainingSeats", ns()).evaluatesTo(0D));
    }

    @Test
    void filtersByFromDate() {
        client.sendRequest(withPayload(request("da-nang-3n2d", LocalDate.now().plusDays(20))))
            .andExpect(xpath("count(//tns:departure)", ns()).evaluatesTo(1D));
    }

    // Slug sai là lỗi của bên gọi -> SOAP Fault dạng CLIENT, để đối tác biết phải sửa request.
    @Test
    void unknownSlugReturnsClientFault() {
        client.sendRequest(withPayload(request("khong-co-that", null)))
            .andExpect(clientOrSenderFault());
    }

    // Tour đã xoá mềm không được lộ ra cho đối tác.
    @Test
    void softDeletedTourIsNotVisible() {
        tour.setDeletedAt(LocalDateTime.now());
        tours.saveAndFlush(tour);

        client.sendRequest(withPayload(request("da-nang-3n2d", null)))
            .andExpect(clientOrSenderFault());
    }

    private StringSource request(String slug, LocalDate fromDate) {
        String from = fromDate == null ? "" : "<tns:fromDate>%s</tns:fromDate>".formatted(fromDate);
        return new StringSource("""
            <tns:getTourAvailabilityRequest xmlns:tns="%s">
                <tns:tourSlug>%s</tns:tourSlug>
                %s
            </tns:getTourAvailabilityRequest>
            """.formatted(NS, slug, from));
    }

    private java.util.Map<String, String> ns() {
        return java.util.Map.of("tns", NS);
    }

    private void saveDeparture(LocalDate date, int total, int booked) {
        Departure departure = new Departure();
        departure.setTour(tour);
        departure.setDepartureDate(date);
        departure.setTotalSeats(total);
        departure.setBookedSeats(booked);
        departures.save(departure);
    }
}
