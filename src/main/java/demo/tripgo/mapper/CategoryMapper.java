package demo.tripgo.mapper;

import demo.tripgo.dto.response.CategoryResponse;
import demo.tripgo.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getSlug(), category.getName());
    }
}
