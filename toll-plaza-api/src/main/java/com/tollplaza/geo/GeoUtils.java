package com.tollplaza.geo;

import com.tollplaza.model.GeoPoint;

public final class GeoUtils {

    public static final double EARTH_RADIUS_KM = 6371.0088;
    /** Length of one degree of latitude (and of longitude at the equator) in km. */
    public static final double KM_PER_DEGREE = EARTH_RADIUS_KM * Math.PI / 180.0;

    private GeoUtils() {
    }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    public static double haversineKm(GeoPoint a, GeoPoint b) {
        return haversineKm(a.latitude(), a.longitude(), b.latitude(), b.longitude());
    }
}
