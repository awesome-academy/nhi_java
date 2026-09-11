package demo.tripgo.service;

import demo.tripgo.dto.response.CategoryResponse;
import demo.tripgo.dto.response.ListResponse;
import demo.tripgo.entity.TourCategory;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class CategoryService {

    // Dữ liệu tĩnh lấy thẳng từ enum nên không cần repository/transaction.
    public ListResponse<CategoryResponse> listCategories() {
        return ListResponse.of(
            Arrays.stream(TourCategory.values())
                .map(category -> new CategoryResponse(category.getSlug(), category.getDisplayName()))
                .toList()
        );
    }
}
