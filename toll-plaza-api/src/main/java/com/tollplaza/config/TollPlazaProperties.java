package com.tollplaza.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application settings, bound from the {@code tollplaza.*} keys in
 * application.yml.
 */
@ConfigurationProperties(prefix = "tollplaza")
public record TollPlazaProperties(
                @DefaultValue Csv csv,
                @DefaultValue Geocoding geocoding,
                @DefaultValue Routing routing,
                @DefaultValue Matching matching,
                @DefaultValue Http http) {

        /**
         * Location of the toll plaza CSV (classpath:, file: or any Spring resource
         * URL).
         */
        public record Csv(@DefaultValue("classpath:data/toll_plaza_india.csv") String path) {
        }

        /** Pincode to coordinates lookup (Nominatim / OpenStreetMap by default). */
        public record Geocoding(
                        @DefaultValue("https://nominatim.openstreetmap.org") String baseUrl,
                        @DefaultValue("toll-plaza-api/1.0") String userAgent,
                        @DefaultValue("1100ms") Duration minInterval) {
        }

        /** Driving route lookup (OSRM by default). */
        public record Routing(@DefaultValue("https://router.project-osrm.org") String baseUrl) {
        }

        /**
         * How close (in km) a toll plaza must be to the route polyline to count as "on
         * the route".
         */
        public record Matching(@DefaultValue("0.5") double thresholdKm) {
        }

        public record Http(
                        @DefaultValue("5s") Duration connectTimeout,
                        @DefaultValue("60s") Duration readTimeout) {
        }
}
