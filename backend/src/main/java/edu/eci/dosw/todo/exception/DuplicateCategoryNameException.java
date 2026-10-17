package edu.eci.dosw.todo.exception;

public class DuplicateCategoryNameException extends RuntimeException {

    public DuplicateCategoryNameException(String name) {
        super("A category named '" + name + "' already exists");
    }
}
