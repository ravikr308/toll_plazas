package com.tollplaza.model;

import java.util.List;

/** A driving route: total length plus the full polyline, ordered from source to destination. */
public record Route(double distanceKm, List<GeoPoint> path) {
}
