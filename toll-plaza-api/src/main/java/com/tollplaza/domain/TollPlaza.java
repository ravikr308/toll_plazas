package com.tollplaza.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "toll_plaza", indexes = @Index(name = "idx_toll_plaza_lat_lon", columnList = "latitude,longitude"))
public class TollPlaza {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    protected TollPlaza() {
    }

    public TollPlaza(String name, double latitude, double longitude) {
        this.id = idFor(name, latitude, longitude);
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /**
     * Deterministic id derived from name and coordinates (5 decimals, roughly 1 m).
     * Re-importing the same CSV row therefore always hits the same database row,
     * which is what makes the import an idempotent upsert.
     */
    public static String idFor(String name, double latitude, double longitude) {
        String key = name.trim().toLowerCase(Locale.ROOT)
                + String.format(Locale.ROOT, "|%.5f|%.5f", latitude, longitude);
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }
}
