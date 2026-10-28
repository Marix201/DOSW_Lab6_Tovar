package edu.eci.dosw.todo.dto;

import edu.eci.dosw.todo.entity.Role;

/** Public view of a user. It never carries the password or its hash. */
public record UserResponse(
        Long id,
        String name,
        String email,
        Role role
) {
}
