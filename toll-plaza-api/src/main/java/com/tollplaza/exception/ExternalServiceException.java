package com.tollplaza.exception;

import org.springframework.http.HttpStatus;

/** An upstream dependency (geocoder or routing engine) failed or returned something unusable. */
public class ExternalServiceException extends ApiException {

    public ExternalServiceException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, message, cause);
    }
}
