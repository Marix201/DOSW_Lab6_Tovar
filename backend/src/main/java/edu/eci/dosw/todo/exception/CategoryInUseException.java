package edu.eci.dosw.todo.exception;

public class CategoryInUseException extends RuntimeException {

    public CategoryInUseException(Long id) {
        super("Category with id " + id + " cannot be deleted because it has associated tasks");
    }
}
