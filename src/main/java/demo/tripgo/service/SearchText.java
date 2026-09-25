package demo.tripgo.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

// Chuẩn hoá chuỗi cho tìm kiếm: bỏ dấu tiếng Việt và hạ chữ thường, GIỮ NGUYÊN mọi ký tự khác.
// "Đà Nẵng 3N2Đ" -> "da nang 3n2d", "50% Off" -> "50% off".
//
// Cố ý khác SlugGenerator (thay dấu cách và ký tự lạ bằng '-'): nếu thay như vậy thì "50%" và
// "50 " sẽ cùng ra "50", và từ khoá "50%" sẽ khớp luôn "50 nights Tour" — làm mất tính literal
// của % mà repo đã có test canh.
//
// Dùng chung đúng một hàm cho cả lúc LƯU (sinh giá trị cột) lẫn lúc TÌM (sinh pattern). Hai bên
// lệch nhau một chút là tìm không ra, nên cố ý không có phiên bản thứ hai.
public final class SearchText {

    public static final char LIKE_ESCAPE_CHAR = '\\';

    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private SearchText() {
    }

    public static String normalize(String... parts) {
        StringBuilder joined = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (!joined.isEmpty()) {
                    joined.append(' ');
                }
                joined.append(part);
            }
        }

        // Normalizer tách dấu thành ký tự tổ hợp rồi xoá đi; riêng đ/Đ không có dạng tổ hợp
        // nên phải thay thủ công trước.
        String ascii = joined.toString().replace('đ', 'd').replace('Đ', 'D');
        ascii = Normalizer.normalize(ascii, Normalizer.Form.NFD);
        ascii = DIACRITICS.matcher(ascii).replaceAll("");

        return WHITESPACE.matcher(ascii.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    // Pattern LIKE cho một từ khoá; rỗng -> '%' khớp tất cả.
    public static String likePattern(String keyword) {
        String normalized = normalize(keyword);
        return normalized.isEmpty() ? "%" : "%" + escapeLike(normalized) + "%";
    }

    // Escape ký tự đặc biệt của LIKE. Phải xử lý '\' trước để không escape lại chính escape char.
    private static String escapeLike(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }
}
