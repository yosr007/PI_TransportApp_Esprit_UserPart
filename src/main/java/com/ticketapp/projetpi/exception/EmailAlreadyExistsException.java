// exception/EmailAlreadyExistsException.java
package com.ticketapp.projetpi.exception;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String email) {
        super("Email already in use: " + email);
    }
}