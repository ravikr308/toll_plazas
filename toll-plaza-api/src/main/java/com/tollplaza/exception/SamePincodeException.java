package com.tollplaza.exception;

import org.springframework.http.HttpStatus;

public class SamePincodeException extends ApiException {

    public static final String MESSAGE = "Source and destination pincodes cannot be the same";

    public SamePincodeException() {
        super(HttpStatus.BAD_REQUEST, MESSAGE);
    }
}
