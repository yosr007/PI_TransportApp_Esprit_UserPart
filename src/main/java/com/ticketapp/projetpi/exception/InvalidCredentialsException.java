// exception/InvalidCredentialsException.java
package com.ticketapp.projetpi.exception;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}