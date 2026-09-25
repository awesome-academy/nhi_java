package demo.tripgo.excel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Đánh dấu một field sẽ thành một cột Excel. ExcelMapper đọc annotation này bằng reflection nên
// thêm loại dữ liệu mới chỉ cần tạo một lớp và gắn annotation, không phải viết thêm code đọc/ghi.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ExcelColumn {

    // Tiêu đề cột. Lúc đọc file, cột được tìm theo tiêu đề (không phân biệt hoa thường) chứ không
    // theo vị trí — người dùng đảo cột hay chèn thêm cột lạ vẫn nhập được.
    String header();

    // Thứ tự cột khi ghi file.
    int order();

    // Bắt buộc: thiếu cột này thì cả file bị từ chối, thiếu giá trị thì dòng đó báo lỗi.
    boolean required() default false;
}
