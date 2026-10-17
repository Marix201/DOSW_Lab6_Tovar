package edu.eci.dosw.todo.dto;

import edu.eci.dosw.todo.entity.CategoryColor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoryUpdateRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 3, max = 40, message = "Name must have between 3 and 40 characters")
        String name,

        @NotNull(message = "Color is required")
        CategoryColor color
) {

    public CategoryUpdateRequest {
        name = name == null ? null : name.strip();
    }
}
