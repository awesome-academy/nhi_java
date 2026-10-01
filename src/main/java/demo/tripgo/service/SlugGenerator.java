package demo.tripgo.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

// Sinh slug từ tiêu đề tiếng Việt: "Đà Nẵng 3N2Đ" -> "da-nang-3n2d".
public final class SlugGenerator {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern NON_SLUG_CHARS = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("^-+|-+$");

    private SlugGenerator() {
    }

    public static String from(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        // Normalizer tách dấu thành ký tự tổ hợp rồi xoá đi; riêng đ/Đ không có dạng tổ hợp
        // nên phải thay thủ công trước.
        String ascii = text.replace('đ', 'd').replace('Đ', 'D');
        ascii = Normalizer.normalize(ascii, Normalizer.Form.NFD);
        ascii = DIACRITICS.matcher(ascii).replaceAll("");

        String slug = NON_SLUG_CHARS.matcher(ascii.toLowerCase(Locale.ROOT)).replaceAll("-");
        return EDGE_DASHES.matcher(slug).replaceAll("");
    }
}
