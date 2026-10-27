package edu.eci.dosw.todo.exception;

/** Raised when a valid token refers to an account that no longer exists. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException() {
        super("Authenticated user no longer exists");
    }
}
