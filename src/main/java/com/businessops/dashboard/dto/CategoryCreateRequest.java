package com.businessops.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for creating a custom category inside a workspace.
 */
public record CategoryCreateRequest(
        @NotBlank(message = "Category name cannot be blank")
        @Size(min = 2, max = 50, message = "Category name must be between 2 and 50 characters")
        String name,

        @NotBlank(message = "Category type cannot be blank")
        String type,

        @Pattern(regexp = "^#([A-Fa-f0-9]{6})$", message = "Color must be a valid 6-digit hex code (e.g. #4F46E5)")
        String color,

        @Size(max = 50, message = "Icon identifier cannot exceed 50 characters")
        String icon
) {
}

