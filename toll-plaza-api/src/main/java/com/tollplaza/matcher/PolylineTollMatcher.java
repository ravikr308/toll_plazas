package com.tollplaza.matcher;

import static com.tollplaza.geo.GeoUtils.KM_PER_DEGREE;

import com.tollplaza.domain.TollPlaza;
import com.tollplaza.geo.GeoUtils;
import com.tollplaza.model.GeoPoint;
import com.tollplaza.model.TollMatch;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Matches plazas against the route polyline: a plaza is on the route when its distance to the
 * nearest polyline segment is within the threshold. Its distance from the source is the length of
 * the polyline up to the projection of the plaza onto that segment.
 */
@Component
public class PolylineTollMatcher implements TollMatcher {

    @Override
    public List<TollMatch> match(List<GeoPoint> path, Collection<TollPlaza> candidates, double thresholdKm) {
        if (path.size() < 2 || candidates.isEmpty()) {
            return List.of();
        }

        int n = path.size();
        double[] lat = new double[n];
        double[] lon = new double[n];
        double[] cumulativeKm = new double[n];
        for (int i = 0; i < n; i++) {
            lat[i] = path.get(i).latitude();
            lon[i] = path.get(i).longitude();
            if (i > 0) {
                cumulativeKm[i] = cumulativeKm[i - 1] + GeoUtils.haversineKm(lat[i - 1], lon[i - 1], lat[i], lon[i]);
            }
        }

        double latEps = thresholdKm / KM_PER_DEGREE;
        List<TollMatch> matches = new ArrayList<>();

        for (TollPlaza plaza : candidates) {
            double pLat = plaza.getLatitude();
            double pLon = plaza.getLongitude();
            double cosLat = Math.max(Math.cos(Math.toRadians(pLat)), 1e-6);
            double lonEps = latEps / cosLat;

            double bestKm = Double.MAX_VALUE;
            double bestAlongKm = 0;

            for (int i = 0; i < n - 1; i++) {
                // Cheap bounding-box rejection before doing any projection maths.
                if (pLat < Math.min(lat[i], lat[i + 1]) - latEps || pLat > Math.max(lat[i], lat[i + 1]) + latEps
                        || pLon < Math.min(lon[i], lon[i + 1]) - lonEps
                        || pLon > Math.max(lon[i], lon[i + 1]) + lonEps) {
                    continue;
                }
                // Local flat projection centred on the plaza (accurate to well under a metre at these scales).
                double ax = (lon[i] - pLon) * KM_PER_DEGREE * cosLat;
                double ay = (lat[i] - pLat) * KM_PER_DEGREE;
                double bx = (lon[i + 1] - pLon) * KM_PER_DEGREE * cosLat;
                double by = (lat[i + 1] - pLat) * KM_PER_DEGREE;
                double dx = bx - ax;
                double dy = by - ay;
                double lengthSquared = dx * dx + dy * dy;
                double t = lengthSquared == 0 ? 0 : -(ax * dx + ay * dy) / lengthSquared;
                t = Math.max(0, Math.min(1, t));
                double distanceKm = Math.hypot(ax + t * dx, ay + t * dy);
                if (distanceKm < bestKm) {
                    bestKm = distanceKm;
                    bestAlongKm = cumulativeKm[i] + t * (cumulativeKm[i + 1] - cumulativeKm[i]);
                }
            }

            if (bestKm <= thresholdKm) {
                matches.add(new TollMatch(plaza, bestAlongKm));
            }
        }

        matches.sort(Comparator.comparingDouble(TollMatch::distanceFromSourceKm)
                .thenComparing(m -> m.plaza().getName()));
        return matches;
    }
}
