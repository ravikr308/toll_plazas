package com.tollplaza.client;

import com.tollplaza.model.GeoPoint;

/** Resolves an Indian pincode to coordinates. Swap the implementation to use another provider. */
public interface GeocodingClient {

    /**
     * @throws com.tollplaza.exception.InvalidPincodeException if the pincode cannot be resolved
     * @throws com.tollplaza.exception.ExternalServiceException if the provider fails
     */
    GeoPoint geocode(String pincode);
}
