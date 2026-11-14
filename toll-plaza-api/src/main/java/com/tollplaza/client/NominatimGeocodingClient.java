package com.tollplaza.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tollplaza.config.CacheConfig;
import com.tollplaza.config.TollPlazaProperties;
import com.tollplaza.exception.ExternalServiceException;
import com.tollplaza.exception.InvalidPincodeException;
import com.tollplaza.model.GeoPoint;
import java.net.URI;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Geocodes a pincode with OpenStreetMap's Nominatim. Nominatim's usage policy allows about one
 * request per second and requires an identifying User-Agent, so calls are spaced out and results
 * are cached.
 */
@Component
public class NominatimGeocodingClient implements GeocodingClient {

    private final RestClient restClient;
    private final TollPlazaProperties.Geocoding settings;
    private final Object throttleLock = new Object();
    private long lastCallNanos;

    public NominatimGeocodingClient(RestClient restClient, TollPlazaProperties properties) {
        this.restClient = restClient;
        this.settings = properties.geocoding();
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.GEOCODES, key = "#pincode")
    public GeoPoint geocode(String pincode) {
        URI uri = UriComponentsBuilder.fromUriString(settings.baseUrl())
                .path("/search")
                .queryParam("postalcode", pincode)
                .queryParam("countrycodes", "in")
                .queryParam("format", "jsonv2")
                .queryParam("limit", 1)
                .build()
                .toUri();
        throttle();
        NominatimPlace[] places;
        try {
            places = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.USER_AGENT, settings.userAgent())
                    .retrieve()
                    .body(NominatimPlace[].class);
        } catch (RestClientException e) {
            throw new ExternalServiceException("Geocoding service is unavailable, please retry later", e);
        }
        if (places == null || places.length == 0) {
            throw new InvalidPincodeException();
        }
        try {
            return new GeoPoint(Double.parseDouble(places[0].lat()), Double.parseDouble(places[0].lon()));
        } catch (NumberFormatException | NullPointerException e) {
            throw new ExternalServiceException("Geocoding service returned an unreadable response", e);
        }
    }

    private void throttle() {
        long minIntervalNanos = settings.minInterval().toNanos();
        if (minIntervalNanos <= 0) {
            return;
        }
        synchronized (throttleLock) {
            long waitNanos = minIntervalNanos - (System.nanoTime() - lastCallNanos);
            if (lastCallNanos != 0 && waitNanos > 0) {
                try {
                    Thread.sleep(waitNanos / 1_000_000, (int) (waitNanos % 1_000_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ExternalServiceException("Interrupted while waiting for geocoding service", e);
                }
            }
            lastCallNanos = System.nanoTime();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NominatimPlace(String lat, String lon) {
    }
}
