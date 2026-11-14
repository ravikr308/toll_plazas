package com.tollplaza.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tollplaza.config.TollPlazaProperties;
import com.tollplaza.exception.ExternalServiceException;
import com.tollplaza.exception.RouteNotFoundException;
import com.tollplaza.model.GeoPoint;
import com.tollplaza.model.Route;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Fetches the full driving polyline from an OSRM server (the public demo server by default). */
@Component
public class OsrmRoutingClient implements RoutingClient {

    private final RestClient restClient;
    private final String baseUrl;

    public OsrmRoutingClient(RestClient restClient, TollPlazaProperties properties) {
        this.restClient = restClient;
        this.baseUrl = properties.routing().baseUrl();
    }

    @Override
    public Route getRoute(GeoPoint from, GeoPoint to) {
        // OSRM wants "lon,lat;lon,lat". Built by hand so ',' and ';' are not percent-encoded.
        String url = String.format(Locale.ROOT,
                "%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=false",
                baseUrl, from.longitude(), from.latitude(), to.longitude(), to.latitude());

        OsrmResponse response;
        try {
            response = restClient.get().uri(URI.create(url)).retrieve().body(OsrmResponse.class);
        } catch (HttpClientErrorException e) {
            if (e.getResponseBodyAsString().contains("NoRoute")) {
                throw new RouteNotFoundException();
            }
            throw new ExternalServiceException("Routing service rejected the request", e);
        } catch (RestClientException e) {
            throw new ExternalServiceException("Routing service is unavailable, please retry later", e);
        }

        if (response == null) {
            throw new ExternalServiceException("Routing service returned an empty response", null);
        }
        if ("NoRoute".equals(response.code())) {
            throw new RouteNotFoundException();
        }
        if (!"Ok".equals(response.code()) || response.routes() == null || response.routes().isEmpty()) {
            throw new ExternalServiceException("Routing service returned an unexpected response", null);
        }

        OsrmRoute route = response.routes().get(0);
        List<GeoPoint> path = new ArrayList<>(route.geometry().coordinates().size());
        for (List<Double> coordinate : route.geometry().coordinates()) {
            path.add(new GeoPoint(coordinate.get(1), coordinate.get(0))); // GeoJSON is [lon, lat]
        }
        return new Route(route.distance() / 1000.0, path);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OsrmResponse(String code, List<OsrmRoute> routes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OsrmRoute(double distance, OsrmGeometry geometry) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OsrmGeometry(List<List<Double>> coordinates) {
    }
}
