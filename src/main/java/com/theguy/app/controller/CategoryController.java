package com.theguy.app.controller;

import com.theguy.app.dto.CategoryDto;
import com.theguy.app.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public List<CategoryDto> all() {
        return categoryService.findAll();
    }

    @GetMapping("/sub")
    public List<CategoryDto> subCategories(@RequestParam UUID parentId) {
        return categoryService.subCategories(parentId);
    }
}