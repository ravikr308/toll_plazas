package com.tollplaza.service;

import com.tollplaza.client.GeocodingClient;
import com.tollplaza.client.RoutingClient;
import com.tollplaza.config.CacheConfig;
import com.tollplaza.config.TollPlazaProperties;
import com.tollplaza.domain.TollPlaza;
import com.tollplaza.dto.RouteSummary;
import com.tollplaza.dto.TollPlazaDetail;
import com.tollplaza.dto.TollPlazaRequest;
import com.tollplaza.dto.TollPlazaResponse;
import com.tollplaza.exception.SamePincodeException;
import com.tollplaza.geo.GeoUtils;
import com.tollplaza.matcher.TollMatcher;
import com.tollplaza.model.GeoPoint;
import com.tollplaza.model.Route;
import com.tollplaza.model.TollMatch;
import com.tollplaza.repository.TollPlazaRepository;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TollPlazaService {

    private final GeocodingClient geocodingClient;
    private final RoutingClient routingClient;
    private final TollPlazaRepository repository;
    private final TollMatcher matcher;
    private final double thresholdKm;

    public TollPlazaService(GeocodingClient geocodingClient, RoutingClient routingClient,
            TollPlazaRepository repository, TollMatcher matcher,
            TollPlazaProperties properties) {
        this.geocodingClient = geocodingClient;
        this.routingClient = routingClient;
        this.repository = repository;
        this.matcher = matcher;
        this.thresholdKm = properties.matching().thresholdKm();
    }

    /**
     * Cached per (source, destination) pair; direction matters because distances
     * are measured from
     * the source. Failed lookups throw, so errors are never cached.
     */
    @Cacheable(cacheNames = CacheConfig.TOLL_PLAZA_RESULTS, key = "#request")
    @Transactional(readOnly = true)
    public TollPlazaResponse findTollPlazas(TollPlazaRequest request) {

        if (request.sourcePincode().equals(request.destinationPincode())) {
            throw new SamePincodeException();
        }

        GeoPoint source = geocodingClient.geocode(request.sourcePincode());
        GeoPoint destination = geocodingClient.geocode(request.destinationPincode());
        Route route = routingClient.getRoute(source, destination);

        List<TollPlaza> candidates = findCandidates(route.path());
        List<TollMatch> matches = matcher.match(route.path(), candidates, thresholdKm);

        List<TollPlazaDetail> tollPlazas = matches.stream()
                .map(m -> new TollPlazaDetail(
                        m.plaza().getName(),
                        m.plaza().getLatitude(),
                        m.plaza().getLongitude(),
                        round(m.distanceFromSourceKm())))
                .toList();

        return new TollPlazaResponse(
                new RouteSummary(request.sourcePincode(), request.destinationPincode(), round(route.distanceKm())),
                tollPlazas);
    }

    /**
     * Narrows the table to plazas inside the route's bounding box so the matcher
     * only sees plausible ones.
     */
    private List<TollPlaza> findCandidates(List<GeoPoint> path) {
        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;
        for (GeoPoint p : path) {
            minLat = Math.min(minLat, p.latitude());
            maxLat = Math.max(maxLat, p.latitude());
            minLon = Math.min(minLon, p.longitude());
            maxLon = Math.max(maxLon, p.longitude());
        }
        double latMargin = thresholdKm / GeoUtils.KM_PER_DEGREE;
        double widestLat = Math.max(Math.abs(minLat), Math.abs(maxLat));
        double lonMargin = latMargin / Math.max(Math.cos(Math.toRadians(widestLat)), 1e-6);
        return repository.findByLatitudeBetweenAndLongitudeBetween(
                minLat - latMargin, maxLat + latMargin, minLon - lonMargin, maxLon + lonMargin);
    }

    private static double round(double km) {
        return Math.round(km * 100.0) / 100.0;
    }
}
