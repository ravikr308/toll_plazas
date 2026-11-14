package com.tollplaza.exception;

import org.springframework.http.HttpStatus;

public class InvalidPincodeException extends ApiException {

    public static final String MESSAGE = "Invalid source or destination pincode";

    public InvalidPincodeException() {
        super(HttpStatus.BAD_REQUEST, MESSAGE);
    }
}
