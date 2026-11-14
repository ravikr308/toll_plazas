package com.tollplaza.client;

import com.tollplaza.model.GeoPoint;
import com.tollplaza.model.Route;

/** Computes a driving route between two points. Swap the implementation to use another provider. */
public interface RoutingClient {

    /**
     * @throws com.tollplaza.exception.RouteNotFoundException if no route exists
     * @throws com.tollplaza.exception.ExternalServiceException if the provider fails
     */
    Route getRoute(GeoPoint from, GeoPoint to);
}
