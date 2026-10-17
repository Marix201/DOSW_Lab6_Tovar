package edu.eci.dosw.todo.exception;

public class InvalidTaskFilterException extends RuntimeException {

    public InvalidTaskFilterException() {
        super("Use either categoryId or uncategorized, not both");
    }
}
