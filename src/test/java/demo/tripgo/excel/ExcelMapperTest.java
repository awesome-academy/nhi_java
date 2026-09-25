package demo.tripgo.excel;

import lombok.Getter;
import lombok.Setter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Kiểm tầng Excel dùng chung: đây là nơi reflection chạy, nên sai ở đây là sai cho MỌI loại dữ liệu.
class ExcelMapperTest {

    private final ExcelMapper mapper = new ExcelMapper();

    @Getter
    @Setter
    public static class SampleRow {
        @ExcelColumn(header = "Tên", order = 1, required = true)
        private String name;

        @ExcelColumn(header = "Số ngày", order = 2)
        private Integer days;

        @ExcelColumn(header = "Giá", order = 3)
        private BigDecimal price;

        @ExcelColumn(header = "Ngày đi", order = 4)
        private LocalDate date;
    }

    // ---- Ghi ----

    @Test
    void writesHeaderInDeclaredOrder() throws Exception {
        byte[] file = mapper.write(List.of(row("Tour A", 3, "1000000", LocalDate.of(2026, 1, 31))),
            SampleRow.class, "Sheet1");

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file))) {
            Row header = workbook.getSheetAt(0).getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Tên");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Số ngày");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("Giá");
            assertThat(header.getCell(3).getStringCellValue()).isEqualTo("Ngày đi");

            Row data = workbook.getSheetAt(0).getRow(1);
            assertThat(data.getCell(0).getStringCellValue()).isEqualTo("Tour A");
            // Số phải ghi dạng số để Excel tính tổng được, không phải chuỗi.
            assertThat(data.getCell(2).getNumericCellValue()).isEqualTo(1_000_000d);
        }
    }

    @Test
    void writesBlankCellForNullValue() throws Exception {
        SampleRow row = new SampleRow();
        row.setName("Chỉ có tên");

        byte[] file = mapper.write(List.of(row), SampleRow.class, "Sheet1");

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file))) {
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getCellType())
                .isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
        }
    }

    // ---- Đọc ----

    @Test
    void readsRowsBackAfterWriting() {
        byte[] file = mapper.write(
            List.of(row("Tour A", 3, "1500000", LocalDate.of(2026, 1, 31))),
            SampleRow.class, "Sheet1");

        ExcelReadResult<SampleRow> result = mapper.read(new ByteArrayInputStream(file), SampleRow.class);

        assertThat(result.errors()).isEmpty();
        SampleRow parsed = result.values().getFirst();
        assertThat(parsed.getName()).isEqualTo("Tour A");
        assertThat(parsed.getDays()).isEqualTo(3);
        assertThat(parsed.getPrice()).isEqualByComparingTo("1500000");
        assertThat(parsed.getDate()).isEqualTo(LocalDate.of(2026, 1, 31));
    }

    // Khớp cột theo tiêu đề chứ không theo vị trí: đảo cột và chèn cột lạ vẫn đọc đúng.
    @Test
    void matchesColumnsByHeaderNotPosition() {
        byte[] file = sheet(
            List.of("Ghi chú", "Số ngày", "Tên"),
            List.of(List.of("bỏ qua", "5", "Tour B")));

        SampleRow parsed = mapper.read(new ByteArrayInputStream(file), SampleRow.class)
            .values().getFirst();

        assertThat(parsed.getName()).isEqualTo("Tour B");
        assertThat(parsed.getDays()).isEqualTo(5);
    }

    @Test
    void headerMatchIsCaseInsensitive() {
        byte[] file = sheet(List.of("TÊN"), List.of(List.of("Tour C")));

        assertThat(mapper.read(new ByteArrayInputStream(file), SampleRow.class)
            .values().getFirst().getName()).isEqualTo("Tour C");
    }

    // ---- Lỗi ----

    @Test
    void missingRequiredColumnRejectsWholeFile() {
        byte[] file = sheet(List.of("Số ngày"), List.of(List.of("3")));

        assertThatThrownBy(() -> mapper.read(new ByteArrayInputStream(file), SampleRow.class))
            .isInstanceOf(ExcelParseException.class)
            .hasMessageContaining("thiếu cột bắt buộc: Tên");
    }

    // Một dòng sai KHÔNG được làm hỏng cả file: đây là điểm mấu chốt của cách nhập này.
    @Test
    void badRowIsReportedButOtherRowsStillParse() {
        byte[] file = sheet(
            List.of("Tên", "Số ngày"),
            List.of(
                List.of("Tour tốt", "3"),
                List.of("Tour sai", "ba ngày"),
                List.of("", "5"),
                List.of("Tour tốt 2", "7")));

        ExcelReadResult<SampleRow> result =
            mapper.read(new ByteArrayInputStream(file), SampleRow.class);

        assertThat(result.values()).hasSize(2);
        assertThat(result.errors()).hasSize(2);
        // Số dòng phải khớp thanh số dòng trong Excel để người dùng tìm đúng chỗ sửa.
        assertThat(result.errors().get(0).rowNumber()).isEqualTo(3);
        assertThat(result.errors().get(0).message()).contains("Số ngày", "ba ngày");
        assertThat(result.errors().get(1).rowNumber()).isEqualTo(4);
        assertThat(result.errors().get(1).message()).contains("thiếu giá trị");
        assertThat(result.totalRows()).isEqualTo(4);
    }

    @Test
    void blankRowsAreSkipped() {
        byte[] file = sheet(
            List.of("Tên"),
            List.of(List.of("Tour A"), List.of(""), List.of("Tour B")));

        ExcelReadResult<SampleRow> result =
            mapper.read(new ByteArrayInputStream(file), SampleRow.class);

        assertThat(result.values()).hasSize(2);
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void nonExcelFileIsRejectedWithReadableMessage() {
        assertThatThrownBy(() -> mapper.read(
            new ByteArrayInputStream("day khong phai excel".getBytes()), SampleRow.class))
            .isInstanceOf(ExcelParseException.class)
            .hasMessageContaining("không phải file Excel hợp lệ");
    }

    @Test
    void emptyFileIsRejected() {
        assertThatThrownBy(() -> mapper.read(new ByteArrayInputStream(new byte[0]), SampleRow.class))
            .isInstanceOf(ExcelParseException.class)
            .hasMessageContaining("rỗng");
    }

    // Giá tiền người dùng hay gõ kèm dấu phân cách nghìn.
    @Test
    void parsesThousandSeparatorsInMoneyColumn() {
        byte[] file = sheet(List.of("Tên", "Giá"), List.of(List.of("Tour A", "4.500.000")));

        assertThat(mapper.read(new ByteArrayInputStream(file), SampleRow.class)
            .values().getFirst().getPrice()).isEqualByComparingTo("4500000");
    }

    @Test
    void acceptsBothDateFormats() {
        byte[] file = sheet(
            List.of("Tên", "Ngày đi"),
            List.of(List.of("A", "31/01/2026"), List.of("B", "2026-01-31")));

        assertThat(mapper.read(new ByteArrayInputStream(file), SampleRow.class).values())
            .extracting(SampleRow::getDate)
            .containsExactly(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 1, 31));
    }

    @Test
    void typeWithoutAnnotationFailsLoudly() {
        assertThatThrownBy(() -> mapper.describe(String.class))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("không có field nào gắn @ExcelColumn");
    }

    // ---- Tiện ích ----

    private SampleRow row(String name, Integer days, String price, LocalDate date) {
        SampleRow row = new SampleRow();
        row.setName(name);
        row.setDays(days);
        row.setPrice(new BigDecimal(price));
        row.setDate(date);
        return row;
    }

    // Dựng file Excel thô từ danh sách chuỗi, để test kiểm được cả những file "người dùng tự gõ".
    private byte[] sheet(List<String> headers, List<List<String>> dataRows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Sheet1");
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
