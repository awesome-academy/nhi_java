package demo.tripgo.admin;

import demo.tripgo.entity.Category;
import demo.tripgo.entity.Destination;
import demo.tripgo.entity.ItineraryDay;
import demo.tripgo.entity.Tour;
import demo.tripgo.entity.TourImage;
import demo.tripgo.repository.CategoryRepository;
import demo.tripgo.repository.DestinationRepository;
import demo.tripgo.repository.TourRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Ghi file thật vào thư mục tạm riêng của test rồi dọn sạch sau mỗi lần chạy.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "storage.upload-dir=./target/test-uploads")
class AdminTourUploadItineraryIntegrationTest {

    private static final Path UPLOAD_DIR = Path.of("./target/test-uploads");

    @Autowired MockMvc mvc;
    @Autowired TourRepository tours;
    @Autowired DestinationRepository destinations;
    @Autowired CategoryRepository categories;

    private Destination destination;
    private Category category;

    @BeforeEach
    void setUp() {
        tours.deleteAll();
        destinations.deleteAll();

        destination = new Destination();
        destination.setName("Đà Nẵng");
        destination.setSlug("da-nang");
        destination = destinations.save(destination);

        category = categories.findBySlug("beach").orElseGet(() -> {
            Category c = new Category();
            c.setSlug("beach");
            c.setName("Biển đảo");
            return categories.save(c);
        });
    }

    @AfterEach
    void cleanUploads() throws IOException {
        if (!Files.exists(UPLOAD_DIR)) {
            return;
        }
        try (var paths = Files.walk(UPLOAD_DIR)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Dọn dẹp best-effort, không để ảnh hưởng kết quả test.
                }
            });
        }
    }

    // ---- Upload ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void uploadsThumbnailAndWritesFileToDisk() throws Exception {
        mvc.perform(newTour().file(image("thumbnailFile", "image/jpeg")))
            .andExpect(redirectedUrl("/admin/tours"));

        Tour saved = tours.findAll().getFirst();
        assertThat(saved.getThumbnailUrl())
            .startsWith("/uploads/")
            .endsWith(".jpg");

        // Đuôi file suy ra từ content type, KHÔNG lấy từ tên file client gửi lên.
        assertThat(fileFor(saved.getThumbnailUrl())).exists();
    }

    // Sai định dạng phải quay lại form kèm lời nhắn, không phải trang 500.
    @Test
    @WithMockUser(roles = "ADMIN")
    void rejectsNonImageFile() throws Exception {
        mvc.perform(newTour().file(new MockMultipartFile(
                "thumbnailFile", "shell.jsp", "application/octet-stream", "x".getBytes())))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .view().name("admin/tours/form"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .content().string(org.hamcrest.Matchers.containsString(
                    "Chỉ chấp nhận ảnh JPG, PNG, WEBP hoặc GIF")));

        assertThat(tours.count()).isZero();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void uploadsMultipleGalleryImages() throws Exception {
        mvc.perform(newTour()
                .file(image("galleryFiles", "image/png"))
                .file(image("galleryFiles", "image/webp")))
            .andExpect(redirectedUrl("/admin/tours"));

        Tour saved = tours.findAll().getFirst();
        List<TourImage> images = loadImages(saved.getId());
        assertThat(images).hasSize(2);
        assertThat(images).extracting(TourImage::getPosition).containsExactly(0, 1);
        images.forEach(image -> assertThat(fileFor(image.getUrl())).exists());
    }

    // Không chọn file mới thì ảnh cũ phải còn — hidden field thumbnailUrl giữ nó lại.
    @Test
    @WithMockUser(roles = "ADMIN")
    void editingWithoutNewFileKeepsExistingThumbnail() throws Exception {
        mvc.perform(newTour().file(image("thumbnailFile", "image/jpeg")));
        Tour saved = tours.findAll().getFirst();
        String original = saved.getThumbnailUrl();

        mvc.perform(editTour(saved.getId()).param("thumbnailUrl", original));

        assertThat(tours.findById(saved.getId()).orElseThrow().getThumbnailUrl()).isEqualTo(original);
        assertThat(fileFor(original)).exists();
    }

    // Tick "Gỡ ảnh này" phải xoá cả bản ghi lẫn file trên đĩa.
    @Test
    @WithMockUser(roles = "ADMIN")
    void removeThumbnailDeletesRecordAndFile() throws Exception {
        mvc.perform(newTour().file(image("thumbnailFile", "image/jpeg")));
        Tour saved = tours.findAll().getFirst();
        String original = saved.getThumbnailUrl();

        mvc.perform(editTour(saved.getId())
            .param("thumbnailUrl", original)
            .param("removeThumbnail", "true"));

        assertThat(tours.findById(saved.getId()).orElseThrow().getThumbnailUrl()).isNull();
        assertThat(fileFor(original)).doesNotExist();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unkeptGalleryImageIsRemoved() throws Exception {
        mvc.perform(newTour()
            .file(image("galleryFiles", "image/png"))
            .file(image("galleryFiles", "image/png")));
        Tour saved = tours.findAll().getFirst();
        List<TourImage> before = loadImages(saved.getId());
        String kept = before.get(0).getUrl();
        String dropped = before.get(1).getUrl();

        // Chỉ gửi lại ảnh muốn giữ; ảnh còn lại coi như đã bỏ tick.
        mvc.perform(editTour(saved.getId()).param("keepImageUrls", kept));

        List<TourImage> after = loadImages(saved.getId());
        assertThat(after).hasSize(1);
        assertThat(after.getFirst().getUrl()).isEqualTo(kept);
        assertThat(fileFor(dropped)).doesNotExist();
    }

    // Nhiều bản ghi trỏ chung một file (dữ liệu gán bằng script): thay ảnh của MỘT tour không
    // được xoá file mà các tour khác vẫn đang dùng.
    @Test
    @WithMockUser(roles = "ADMIN")
    void sharedImageIsKeptWhenOtherRecordsStillUseIt() throws Exception {
        mvc.perform(newTour().file(image("thumbnailFile", "image/jpeg")));
        Tour first = tours.findAll().getFirst();
        String shared = first.getThumbnailUrl();

        // Tour thứ hai dùng chung đúng file đó.
        mvc.perform(newTour().param("slug", "tour-hai").param("thumbnailUrl", shared));
        assertThat(tours.count()).isEqualTo(2);

        // Gỡ ảnh của tour thứ nhất -> file phải còn, vì tour thứ hai vẫn dùng.
        mvc.perform(editTour(first.getId())
            .param("thumbnailUrl", shared)
            .param("removeThumbnail", "true"));

        assertThat(tours.findById(first.getId()).orElseThrow().getThumbnailUrl()).isNull();
        assertThat(fileFor(shared)).exists();
    }

    // Ngược lại: chỉ còn mình nó dùng thì xoá file như cũ, không để rác lại trên đĩa.
    @Test
    @WithMockUser(roles = "ADMIN")
    void soleReferenceStillDeletesTheFile() throws Exception {
        mvc.perform(newTour().file(image("thumbnailFile", "image/jpeg")));
        Tour only = tours.findAll().getFirst();
        String url = only.getThumbnailUrl();

        mvc.perform(editTour(only.getId())
            .param("thumbnailUrl", url)
            .param("removeThumbnail", "true"));

        assertThat(fileFor(url)).doesNotExist();
    }

    // ---- Lịch trình ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void savesItineraryDaysInOrder() throws Exception {
        mvc.perform(newTour()
                .param("itinerary[0].title", "Khởi hành")
                .param("itinerary[0].description", "Xe đón tại khách sạn")
                .param("itinerary[1].title", "Bà Nà Hills")
                .param("itinerary[1].description", "Cả ngày"))
            .andExpect(redirectedUrl("/admin/tours"));

        List<ItineraryDay> days = loadItinerary(tours.findAll().getFirst().getId());
        assertThat(days).extracting(ItineraryDay::getDayNumber).containsExactly(1, 2);
        assertThat(days).extracting(ItineraryDay::getTitle)
            .containsExactly("Khởi hành", "Bà Nà Hills");
    }

    // Dòng trống do admin bấm "Thêm ngày" rồi không nhập: bỏ qua, không báo lỗi bắt buộc.
    @Test
    @WithMockUser(roles = "ADMIN")
    void blankItineraryRowsAreIgnored() throws Exception {
        mvc.perform(newTour()
            .param("itinerary[0].title", "Ngày thật")
            .param("itinerary[1].title", "")
            .param("itinerary[1].description", "   "));

        assertThat(loadItinerary(tours.findAll().getFirst().getId())).hasSize(1);
    }

    // Xoá dòng giữa: số ngày phải đánh lại liên tục, không để 1, 3.
    @Test
    @WithMockUser(roles = "ADMIN")
    void renumbersDaysAfterMiddleRowRemoved() throws Exception {
        mvc.perform(newTour()
            .param("itinerary[0].title", "A")
            .param("itinerary[1].title", "B")
            .param("itinerary[2].title", "C"));
        Long id = tours.findAll().getFirst().getId();

        mvc.perform(editTour(id)
            .param("itinerary[0].title", "A")
            .param("itinerary[1].title", "C"));

        List<ItineraryDay> days = loadItinerary(id);
        assertThat(days).extracting(ItineraryDay::getDayNumber).containsExactly(1, 2);
        assertThat(days).extracting(ItineraryDay::getTitle).containsExactly("A", "C");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void itineraryShowsUpInPublicTourDetail() throws Exception {
        mvc.perform(newTour()
            .param("itinerary[0].title", "Khởi hành")
            .param("itinerary[0].description", "Xe đón tại khách sạn"));
        Tour saved = tours.findAll().getFirst();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/v1/tours/" + saved.getId()))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .jsonPath("$.itinerary[0].title").value("Khởi hành"));
    }

    // ---- Tiện ích ----

    private MockMultipartHttpServletRequestBuilder newTour() {
        return withBaseFields(multipart("/admin/tours"));
    }

    private MockMultipartHttpServletRequestBuilder editTour(Long id) {
        return withBaseFields(multipart("/admin/tours/" + id));
    }

    private MockMultipartHttpServletRequestBuilder withBaseFields(
        MockMultipartHttpServletRequestBuilder builder
    ) {
        builder.with(csrf());
        builder.param("title", "Đà Nẵng 3N2Đ");
        builder.param("slug", "da-nang-3n2d");
        builder.param("destinationId", destination.getId().toString());
        builder.param("categoryId", category.getId().toString());
        builder.param("price", "4500000");
        builder.param("durationDays", "3");
        builder.param("maxGuests", "20");
        return builder;
    }

    private MockMultipartFile image(String field, String contentType) {
        return new MockMultipartFile(field, "anh.png", contentType, "fake-image-bytes".getBytes());
    }

    private Path fileFor(String publicUrl) {
        return UPLOAD_DIR.resolve(publicUrl.substring("/uploads/".length()));
    }

    private List<TourImage> loadImages(Long tourId) {
        return tours.findDetailById(tourId).orElseThrow().getImages().stream()
            .sorted(Comparator.comparingInt(TourImage::getPosition))
            .toList();
    }

    private List<ItineraryDay> loadItinerary(Long tourId) {
        return tours.fetchItinerary(tourId).orElseThrow().getItinerary().stream()
            .sorted(Comparator.comparingInt(ItineraryDay::getDayNumber))
            .toList();
    }
}
