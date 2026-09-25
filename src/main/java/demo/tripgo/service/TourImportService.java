package demo.tripgo.service;

import demo.tripgo.admin.excel.ImportSummary;
import demo.tripgo.admin.excel.TourImportRow;
import demo.tripgo.excel.ExcelMapper;
import demo.tripgo.excel.ExcelReadResult;
import demo.tripgo.excel.ExcelRow;
import demo.tripgo.excel.ExcelRowError;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class TourImportService {

    private final ExcelMapper excelMapper;
    private final TourRowImporter rowImporter;

    public TourImportService(ExcelMapper excelMapper, TourRowImporter rowImporter) {
        this.excelMapper = excelMapper;
        this.rowImporter = rowImporter;
    }

    // Nhập được dòng nào hay dòng đó, dòng lỗi báo lại kèm số dòng. Từ chối cả file chỉ vì một ô
    // gõ nhầm sẽ biến việc nhập 200 tour thành vòng lặp sửa-thử vô tận.
    //
    // KHÔNG @Transactional ở mức cả file: một dòng vi phạm ràng buộc DB sẽ kéo đổ những dòng đã
    // lưu trước đó. Mỗi dòng là một transaction riêng (TourRowImporter, REQUIRES_NEW).
    public ImportSummary importTours(InputStream input) {
        ExcelReadResult<TourImportRow> result = read(input);

        List<ExcelRowError> errors = new ArrayList<>(result.errors());
        int imported = 0;

        for (ExcelRow<TourImportRow> row : result.rows()) {
            try {
                if (rowImporter.save(row.value(), slugOf(row.value()))) {
                    imported++;
                } else {
                    errors.add(new ExcelRowError(row.rowNumber(),
                        "tour có slug '" + slugOf(row.value()) + "' đã tồn tại, bỏ qua"));
                }
            } catch (ImportRowException exception) {
                errors.add(new ExcelRowError(row.rowNumber(), exception.getMessage()));
            }
        }

        errors.sort(java.util.Comparator.comparingInt(ExcelRowError::rowNumber));
        // skipped tính từ tổng trừ đi số đã nhập, để ba con số trên màn hình luôn cộng đúng.
        // Nếu đếm riêng thì dòng hỏng định dạng ô (ExcelMapper bắt) sẽ không được tính vào đâu cả,
        // và admin thấy "5 dòng, nhập 2, bỏ qua 2" — thiếu mất một dòng.
        return new ImportSummary(
            result.totalRows(), imported, result.totalRows() - imported, errors);
    }

    private ExcelReadResult<TourImportRow> read(InputStream input) {
        try (input) {
            return excelMapper.read(input, TourImportRow.class);
        } catch (IOException exception) {
            throw new UncheckedIOException("Không đọc được file tải lên", exception);
        }
    }

    private String slugOf(TourImportRow row) {
        String slug = row.getSlug() == null || row.getSlug().isBlank()
            ? SlugGenerator.from(row.getTitle())
            : SlugGenerator.from(row.getSlug());
        return slug.isEmpty() ? "tour" : slug;
    }

    // File mẫu: đúng các cột mà TourImportRow khai, kèm một dòng ví dụ để người dùng biết định dạng.
    public byte[] template() {
        TourImportRow sample = new TourImportRow();
        sample.setTitle("Đà Nẵng 3N2Đ");
        sample.setSlug("da-nang-3n2d");
        sample.setDestinationSlug("da-nang");
        sample.setCategorySlug("beach");
        sample.setPrice(new BigDecimal("4500000"));
        sample.setDiscountPrice(new BigDecimal("3900000"));
        sample.setDurationDays(3);
        sample.setMaxGuests(20);
        sample.setDescription("Mô tả ngắn về tour");
        return excelMapper.write(List.of(sample), TourImportRow.class, "Tour");
    }
}
