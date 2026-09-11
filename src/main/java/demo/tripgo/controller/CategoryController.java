package demo.tripgo.controller;

import demo.tripgo.dto.response.CategoryResponse;
import demo.tripgo.dto.response.ListResponse;
import demo.tripgo.service.CategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<ListResponse<CategoryResponse>> listCategories() {
        return ResponseEntity.ok(categoryService.listCategories());
    }
}
