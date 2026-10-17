package edu.eci.dosw.todo.dto;

import edu.eci.dosw.todo.entity.CategoryColor;

public record CategoryResponse(
        Long id,
        String name,
        CategoryColor color
) {
}
