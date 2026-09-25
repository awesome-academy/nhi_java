package demo.tripgo.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTextTest {

    @Test
    void stripsVietnameseDiacritics() {
        assertThat(SearchText.normalize("Đà Nẵng 3N2Đ")).isEqualTo("da nang 3n2d");
        assertThat(SearchText.normalize("Huế")).isEqualTo("hue");
        assertThat(SearchText.normalize("Lễ hội hoa Đà Lạt")).isEqualTo("le hoi hoa da lat");
    }

    @Test
    void joinsSeveralParts() {
        assertThat(SearchText.normalize("Đà Nẵng", "Biển đẹp")).isEqualTo("da nang bien dep");
        assertThat(SearchText.normalize("Đà Nẵng", null)).isEqualTo("da nang");
    }

    // Từ khoá và giá trị lưu phải đi qua cùng một hàm, nếu không sẽ tìm không ra.
    @Test
    void keywordAndStoredValueNormalizeTheSameWay() {
        String stored = SearchText.normalize("Đà Nẵng 3N2Đ", "Tour biển");
        assertThat(stored).contains(SearchText.normalize("da nang"));
        assertThat(stored).contains(SearchText.normalize("ĐÀ NẴNG"));
    }

    @Test
    void blankKeywordMatchesEverything() {
        assertThat(SearchText.likePattern(null)).isEqualTo("%");
        assertThat(SearchText.likePattern("   ")).isEqualTo("%");
    }

    // % và _ được GIỮ LẠI rồi escape, để chúng là ký tự thường chứ không phải wildcard:
    // gõ "50%" không được khớp luôn "50 nights".
    @Test
    void likeWildcardsAreEscapedNotStripped() {
        assertThat(SearchText.likePattern("%")).isEqualTo("%\\%%");
        assertThat(SearchText.likePattern("_")).isEqualTo("%\\_%");
        assertThat(SearchText.likePattern("50%")).isEqualTo("%50\\%%");
        assertThat(SearchText.likePattern("Đà Nẵng")).isEqualTo("%da nang%");
    }
}
