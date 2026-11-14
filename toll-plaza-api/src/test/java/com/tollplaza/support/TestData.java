package com.tollplaza.support;

import com.tollplaza.config.TollPlazaProperties;
import com.tollplaza.model.GeoPoint;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class TestData {

    private TestData() {
    }

    /** Settings with no throttling and the default 0.5 km matching threshold. */
    public static TollPlazaProperties properties() {
        return new TollPlazaProperties(
                new TollPlazaProperties.Csv("classpath:test-data/toll_plazas.csv"),
                new TollPlazaProperties.Geocoding("http://nominatim.test", "test-agent", Duration.ZERO),
                new TollPlazaProperties.Routing("http://osrm.test"),
                new TollPlazaProperties.Matching(0.5),
                new TollPlazaProperties.Http(Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }

    /** A straight north-bound route along longitude 77.0 from latitude 28.0 to 29.0 (about 111.2 km). */
    public static List<GeoPoint> northboundPath() {
        List<GeoPoint> path = new ArrayList<>();
        for (int i = 0; i <= 10; i++) {
            path.add(new GeoPoint(28.0 + i / 10.0, 77.0));
        }
        return path;
    }
}
