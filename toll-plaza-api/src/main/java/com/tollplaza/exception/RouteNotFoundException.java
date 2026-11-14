package com.tollplaza.exception;

import org.springframework.http.HttpStatus;

public class RouteNotFoundException extends ApiException {

    public RouteNotFoundException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "No driving route found between the given pincodes");
    }
}
