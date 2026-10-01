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
import demo.tripgo.excel.ExcelFormats;
import demo.tripgo.repository.BookingRepository;
import demo.tripgo.repository.CategoryRepository;
import demo.tripgo.repository.DepartureRepository;
import demo.tripgo.repository.DestinationRepository;
import demo.tripgo.repository.TourRepository;
import demo.tripgo.repository.UserRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminExcelExportIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TourRepository tours;
    @Autowired DestinationRepository destinations;
    @Autowired CategoryRepository categories;
    @Autowired BookingRepository bookings;
    @Autowired DepartureRepository departures;
    @Autowired UserRepository users;
    @Autowired demo.tripgo.TestDataCleaner cleaner;

    private Destination destination;
    private Tour tour;
    private Departure departure;

    @BeforeEach
    void setUp() {
        cleaner.clean();

        destination = new Destination();
        destination.setName("Đà Nẵng");
        destination.setSlug("da-nang");
        destination = destinations.save(destination);

        tour = saveTour("Đà Nẵng 3N2Đ");

        departure = new Departure();
        departure.setTour(tour);
        departure.setDepartureDate(LocalDate.now().plusDays(30));
        departure.setTotalSeats(20);
        departure.setBookedSeats(0);
        departure = departures.save(departure);
    }

    // ---- Đơn đặt ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void exportsBookingsAsXlsxWithHeaderAndData() throws Exception {
        saveBooking(BookingStatus.PENDING, "9000000");

        MvcResult result = mvc.perform(get("/admin/bookings/export"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("Content-Disposition",
                org.hamcrest.Matchers.containsString("attachment")))
            .andReturn();

        List<List<String>> rows = readSheet(result.getResponse().getContentAsByteArray());
        assertThat(rows.getFirst()).containsExactly(
            "Mã đơn", "Khách", "Email", "Tour", "Ngày đi", "Số khách", "Tổng tiền", "Trạng thái");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1)).contains("Đà Nẵng 3N2Đ", "Chờ xác nhận");
        assertThat(formatOf(result.getResponse().getContentAsByteArray(), 6)).isEqualTo(ExcelFormats.VND);
    }

    // Xuất phải theo ĐÚNG bộ lọc đang xem, không phải xuất tất cả.
    @Test
    @WithMockUser(roles = "ADMIN")
    void exportRespectsStatusFilter() throws Exception {
        saveBooking(BookingStatus.PENDING, "1000000");
        saveBooking(BookingStatus.CONFIRMED, "2000000");

        assertThat(dataRowCount(export("/admin/bookings/export"))).isEqualTo(2);
        assertThat(dataRowCount(export("/admin/bookings/export?status=CONFIRMED"))).isEqualTo(1);
        assertThat(dataRowCount(export("/admin/bookings/export?status=CANCELLED"))).isZero();
    }

    // Không có dữ liệu vẫn phải ra file hợp lệ (chỉ có dòng tiêu đề), không phải lỗi.
    @Test
    @WithMockUser(roles = "ADMIN")
    void emptyResultStillProducesValidFile() throws Exception {
        List<List<String>> rows = readSheet(export("/admin/bookings/export"));

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst()).first().isEqualTo("Mã đơn");
    }

    // ---- Tour ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void exportsToursAndRespectsSearch() throws Exception {
        saveTour("Sapa săn mây");

        assertThat(dataRowCount(export("/admin/tours/export"))).isEqualTo(2);
        assertThat(dataRowCount(export("/admin/tours/export?q=sapa"))).isEqualTo(1);
    }

    // Giá và giá khuyến mãi hiện kèm VNĐ nhưng vẫn là số; điểm đánh giá làm tròn thật 1 chữ số.
    @Test
    @WithMockUser(roles = "ADMIN")
    void tourExportFormatsPriceInVndAndRoundsRating() throws Exception {
        tour.setDiscountPrice(new BigDecimal("3990000"));
        tour.setRatingAvg(4.35);
        tours.save(tour);

        byte[] file = export("/admin/tours/export");
        List<List<String>> rows = readSheet(file);

        assertThat(rows.get(1).get(5)).isEqualTo("4500000");
        assertThat(rows.get(1).get(6)).isEqualTo("3990000");
        assertThat(formatOf(file, 5)).isEqualTo(ExcelFormats.VND);
        assertThat(formatOf(file, 6)).isEqualTo(ExcelFormats.VND);
        // 4.35 -> 4.4 (HALF_UP); giá trị trong ô là 4.4 thật, không phải 4.35 hiển thị thành 4.4.
        assertThat(rows.get(1).get(8)).isEqualTo("4.4");
        assertThat(formatOf(file, 8)).isEqualTo(ExcelFormats.ONE_DECIMAL);
    }

    // ---- Bảo vệ ----

    @Test
    @WithMockUser(roles = "USER")
    void normalUserCannotExport() throws Exception {
        mvc.perform(get("/admin/bookings/export"))
            .andExpect(redirectedUrl("/admin/login?denied"));
    }

    @Test
    void anonymousCannotExport() throws Exception {
        mvc.perform(get("/admin/tours/export"))
            .andExpect(redirectedUrl("/admin/login"));
    }

    // ---- Tiện ích ----

    private byte[] export(String url) throws Exception {
        return mvc.perform(get(url))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    }

    // Mã định dạng của ô dữ liệu đầu tiên ở cột column.
    private String formatOf(byte[] file, int column) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file))) {
            return workbook.getSheetAt(0).getRow(1).getCell(column).getCellStyle().getDataFormatString();
        } catch (Exception exception) {
            throw new IllegalStateException("File xuất ra không đọc được", exception);
        }
    }

    private int dataRowCount(byte[] file) {
        return readSheet(file).size() - 1;
    }

    private List<List<String>> readSheet(byte[] file) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            List<List<String>> rows = new ArrayList<>();
            for (int index = 0; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null) {
                    continue;
                }
                List<String> cells = new ArrayList<>();
                for (int column = 0; column < row.getLastCellNum(); column++) {
                    cells.add(cellText(row, column));
                }
                rows.add(cells);
            }
            return rows;
        } catch (Exception exception) {
            throw new IllegalStateException("File xuất ra không đọc được", exception);
        }
    }

    private String cellText(Row row, int column) {
        var cell = row.getCell(column);
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double value = cell.getNumericCellValue();
                yield value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
            }
            default -> "";
        };
    }

    private Tour saveTour(String title) {
        Category category = categories.findBySlug("beach").orElseGet(() -> {
            Category c = new Category();
            c.setSlug("beach");
            c.setName("Biển đảo");
            return categories.save(c);
        });

        Tour t = new Tour();
        t.setTitle(title);
        t.setSlug(UUID.randomUUID().toString());
        t.setDestination(destination);
        t.setCategory(category);
        t.setDurationDays(3);
        t.setPrice(new BigDecimal("4500000"));
        t.setMaxGuests(20);
        return tours.save(t);
    }

    private void saveBooking(BookingStatus status, String total) {
        User user = new User();
        user.setFullName("Khách");
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPassword("x");
        user.setRole(Role.USER);
        user = users.save(user);

        ContactInfo contact = new ContactInfo();
        contact.setFullName("Nguyễn A");
        contact.setEmail("a@example.com");
        contact.setPhone("0900000000");

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setTour(tour);
        booking.setDeparture(departure);
        booking.setCode("TG-" + UUID.randomUUID().toString().substring(0, 8));
        booking.setAdults(2);
        booking.setChildren(0);
        booking.setTotalPrice(new BigDecimal(total));
        booking.setStatus(status);
        booking.setContact(contact);
        bookings.save(booking);
    }
}
