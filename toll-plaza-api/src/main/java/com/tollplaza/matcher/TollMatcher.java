package com.tollplaza.matcher;

import com.tollplaza.domain.TollPlaza;
import com.tollplaza.model.GeoPoint;
import com.tollplaza.model.TollMatch;
import java.util.Collection;
import java.util.List;

/** Strategy for deciding which candidate toll plazas lie on a route. */
public interface TollMatcher {

    /**
     * @param path        route polyline ordered from source to destination
     * @param candidates  plazas to test
     * @param thresholdKm maximum distance from the polyline for a plaza to count as on the route
     * @return matches ordered by distance from the source
     */
    List<TollMatch> match(List<GeoPoint> path, Collection<TollPlaza> candidates, double thresholdKm);
}
