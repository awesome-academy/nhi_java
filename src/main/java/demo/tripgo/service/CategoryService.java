package demo.tripgo.service;

import demo.tripgo.dto.response.CategoryResponse;
import demo.tripgo.dto.response.ListResponse;
import demo.tripgo.mapper.CategoryMapper;
import demo.tripgo.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryRepository categoryRepository, CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
    }

    public ListResponse<CategoryResponse> listCategories() {
        return ListResponse.of(
            categoryRepository.findAllByOrderByIdAsc().stream()
                .map(categoryMapper::toResponse)
                .toList()
        );
    }
}
