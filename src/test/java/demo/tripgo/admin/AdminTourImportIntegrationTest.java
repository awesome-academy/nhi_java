package demo.tripgo.admin;

import demo.tripgo.TestDataCleaner;
import demo.tripgo.admin.excel.ImportSummary;
import demo.tripgo.excel.ExcelRowError;
import demo.tripgo.entity.Category;
import demo.tripgo.entity.Destination;
import demo.tripgo.entity.Tour;
import demo.tripgo.repository.CategoryRepository;
import demo.tripgo.repository.DestinationRepository;
import demo.tripgo.repository.TourRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminTourImportIntegrationTest {

    private static final List<String> HEADERS = List.of(
        "Tên tour", "Slug", "Điểm đến", "Loại hình", "Giá",
        "Giá khuyến mãi", "Số ngày", "Số khách tối đa", "Mô tả");

    @Autowired MockMvc mvc;
    @Autowired TestDataCleaner cleaner;
    @Autowired TourRepository tours;
    @Autowired DestinationRepository destinations;
    @Autowired CategoryRepository categories;

    @BeforeEach
    void setUp() {
        cleaner.clean();

        Destination destination = new Destination();
        destination.setName("Đà Nẵng");
        destination.setSlug("da-nang");
        destinations.save(destination);

        categories.findBySlug("beach").orElseGet(() -> {
            Category category = new Category();
            category.setSlug("beach");
            category.setName("Biển đảo");
            return categories.save(category);
        });
    }

    // ---- Luồng chính ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void importsValidRows() throws Exception {
        upload(List.of(
            row("Đà Nẵng 3N2Đ", "", "da-nang", "beach", "4500000", "3900000", "3", "20", "Mô tả"),
            row("Sapa săn mây", "sapa-san-may", "da-nang", "beach", "3200000", "", "2", "15", "")))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/tours/import"))
            .andExpect(content().string(containsString("không có dòng nào lỗi")));

        assertThat(tours.count()).isEqualTo(2);
        Tour saved = tours.findAll().stream()
            .filter(t -> t.getTitle().equals("Đà Nẵng 3N2Đ")).findFirst().orElseThrow();
        // Slug bỏ trống -> tự sinh từ tên, bỏ dấu.
        assertThat(saved.getSlug()).isEqualTo("da-nang-3n2d");
        assertThat(saved.getPrice()).isEqualByComparingTo("4500000");
        assertThat(saved.getDiscountPrice()).isEqualByComparingTo("3900000");
        assertThat(saved.getMaxGuests()).isEqualTo(20);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void importedTourAppearsInPublicApi() throws Exception {
        upload(List.of(row("Tour nhập từ Excel", "", "da-nang", "beach", "1000000", "", "2", "10", "")));

        mvc.perform(get("/api/v1/tours"))
            .andExpect(content().string(containsString("Tour nhập từ Excel")));
    }

    // ---- Nhập được dòng nào hay dòng đó ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void badRowsAreReportedButGoodRowsStillImport() throws Exception {
        MvcResult result = upload(List.of(
            row("Tour tốt", "tour-tot", "da-nang", "beach", "1000000", "", "3", "20", ""),
            row("Sai điểm đến", "sai-diem-den", "khong-co", "beach", "1000000", "", "3", "20", ""),
            row("Sai loại hình", "sai-loai", "da-nang", "khong-co", "1000000", "", "3", "20", ""),
            row("Số ngày hỏng", "so-ngay-hong", "da-nang", "beach", "1000000", "", "ba", "20", ""),
            row("Tour tốt 2", "tour-tot-2", "da-nang", "beach", "2000000", "", "4", "20", "")))
            .andExpect(status().isOk())
            .andReturn();

        // Kiểm qua model: Thymeleaf escape dấu nháy trong HTML ("'" -> "&#39;") nên so chuỗi thô
        // trên response sẽ trượt dù thông báo hoàn toàn đúng.
        ImportSummary summary = summaryOf(result);
        assertThat(summary.totalRows()).isEqualTo(5);
        assertThat(summary.imported()).isEqualTo(2);
        assertThat(summary.skipped()).isEqualTo(3);
        assertThat(summary.errors()).extracting(ExcelRowError::message)
            .anySatisfy(message -> assertThat(message).contains("không có điểm đến", "khong-co"))
            .anySatisfy(message -> assertThat(message).contains("không có loại hình", "khong-co"))
            .anySatisfy(message -> assertThat(message).contains("Số ngày", "ba"));
        // Số dòng phải khớp thanh số dòng Excel để admin tìm đúng chỗ sửa.
        assertThat(summary.errors()).extracting(ExcelRowError::rowNumber).containsExactly(3, 4, 5);

        assertThat(tours.count()).isEqualTo(2);
        assertThat(tours.findAll()).extracting(Tour::getSlug)
            .containsExactlyInAnyOrder("tour-tot", "tour-tot-2");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rejectsDiscountNotLowerThanPrice() throws Exception {
        upload(List.of(row("Tour A", "tour-a", "da-nang", "beach", "1000000", "1000000", "3", "20", "")))
            .andExpect(content().string(containsString("giá khuyến mãi phải nhỏ hơn giá gốc")));

        assertThat(tours.count()).isZero();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void missingRequiredValueIsReportedPerRow() throws Exception {
        upload(List.of(row("", "", "da-nang", "beach", "1000000", "", "3", "20", "")))
            .andExpect(content().string(containsString("thiếu giá trị ở cột bắt buộc")));

        assertThat(tours.count()).isZero();
    }

    // ---- Trùng slug thì bỏ qua, không ghi đè ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void duplicateSlugIsSkippedNotOverwritten() throws Exception {
        upload(List.of(row("Tên gốc", "tour-a", "da-nang", "beach", "1000000", "", "3", "20", "")));
        assertThat(tours.count()).isEqualTo(1);

        upload(List.of(row("Tên mới", "tour-a", "da-nang", "beach", "9000000", "", "9", "99", "")))
            .andExpect(content().string(containsString("đã tồn tại")));

        assertThat(tours.count()).isEqualTo(1);
        Tour kept = tours.findAll().getFirst();
        assertThat(kept.getTitle()).isEqualTo("Tên gốc");
        assertThat(kept.getPrice()).isEqualByComparingTo("1000000");
    }

    // Nhập lại đúng file cũ không được tạo bản sao.
    @Test
    @WithMockUser(roles = "ADMIN")
    void reimportingSameFileIsIdempotent() throws Exception {
        List<List<String>> rows =
            List.of(row("Tour A", "tour-a", "da-nang", "beach", "1000000", "", "3", "20", ""));

        upload(rows);
        upload(rows);

        assertThat(tours.count()).isEqualTo(1);
    }

    // Tour đã xoá mềm vẫn giữ slug (có hậu tố) nên không chặn tour mới cùng tên.
    @Test
    @WithMockUser(roles = "ADMIN")
    void softDeletedTourDoesNotBlockImportOfSameSlug() throws Exception {
        upload(List.of(row("Tour A", "tour-a", "da-nang", "beach", "1000000", "", "3", "20", "")));
        Tour existing = tours.findAll().getFirst();
        existing.setDeletedAt(LocalDateTime.now());
        existing.setSlug("tour-a-deleted-" + existing.getId());
        tours.saveAndFlush(existing);

        upload(List.of(row("Tour A mới", "tour-a", "da-nang", "beach", "2000000", "", "3", "20", "")));

        assertThat(tours.count()).isEqualTo(2);
    }

    // ---- Lỗi ở mức cả file ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void missingRequiredColumnRejectsWholeFile() throws Exception {
        byte[] file = sheet(List.of("Slug", "Giá"), List.of(List.of("tour-a", "1000000")));

        mvc.perform(multipart("/admin/tours/import").file(excelFile(file)).with(csrf()))
            .andExpect(content().string(containsString("thiếu cột bắt buộc")));

        assertThat(tours.count()).isZero();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void nonExcelFileIsRejected() throws Exception {
        mvc.perform(multipart("/admin/tours/import")
                .file(new MockMultipartFile("file", "a.xlsx",
                    MediaType.APPLICATION_OCTET_STREAM_VALUE, "khong phai excel".getBytes()))
                .with(csrf()))
            .andExpect(content().string(containsString("không phải file Excel hợp lệ")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void emptyUploadAsksForFile() throws Exception {
        mvc.perform(multipart("/admin/tours/import")
                .file(new MockMultipartFile("file", "", MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[0]))
                .with(csrf()))
            .andExpect(content().string(containsString("Vui lòng chọn file Excel")));
    }

    // ---- File mẫu ----

    // File mẫu sinh từ chính TourImportRow nên phải nhập lại được ngay, không cần sửa gì.
    @Test
    @WithMockUser(roles = "ADMIN")
    void templateCanBeImportedBack() throws Exception {
        MvcResult result = mvc.perform(get("/admin/tours/import/template"))
            .andExpect(status().isOk())
            .andReturn();

        mvc.perform(multipart("/admin/tours/import")
                .file(excelFile(result.getResponse().getContentAsByteArray()))
                .with(csrf()))
            .andExpect(content().string(containsString("không có dòng nào lỗi")));

        assertThat(tours.count()).isEqualTo(1);
    }

    // ---- Bảo vệ ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void uploadWithoutCsrfIsRejected() throws Exception {
        mvc.perform(multipart("/admin/tours/import")
                .file(excelFile(sheet(HEADERS, List.of()))))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void normalUserCannotImport() throws Exception {
        mvc.perform(get("/admin/tours/import"))
            .andExpect(redirectedUrl("/admin/login?denied"));
    }

    // ---- Tiện ích ----

    private org.springframework.test.web.servlet.ResultActions upload(List<List<String>> rows)
        throws Exception {
        return mvc.perform(multipart("/admin/tours/import")
            .file(excelFile(sheet(HEADERS, rows)))
            .with(csrf()));
    }

    private ImportSummary summaryOf(MvcResult result) {
        Object summary = result.getModelAndView().getModel().get("summary");
        assertThat(summary).isInstanceOf(ImportSummary.class);
        return (ImportSummary) summary;
    }

    private MockMultipartFile excelFile(byte[] content) {
        return new MockMultipartFile("file", "tour.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
    }

    private List<String> row(String... values) {
        return List.of(values);
    }

    private byte[] sheet(List<String> headers, List<List<String>> dataRows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Tour");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                header.createCell(i).setCellValue(headers.get(i));
            }
            for (int r = 0; r < dataRows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<String> values = dataRows.get(r);
                for (int c = 0; c < values.size(); c++) {
                    row.createCell(c).setCellValue(values.get(c));
                }
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

}
