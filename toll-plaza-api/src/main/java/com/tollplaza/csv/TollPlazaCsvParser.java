package com.tollplaza.csv;

import com.tollplaza.domain.TollPlaza;
import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Parses the toll plaza CSV. Column headers are matched loosely (case, spaces, underscores and a
 * leading BOM are ignored) against a few common aliases, so minor differences in the file's header
 * row do not require code changes. Bad rows are skipped and counted rather than failing the import.
 */
@Component
public class TollPlazaCsvParser {

    private static final Set<String> NAME_ALIASES =
            Set.of("name", "tollplazaname", "tollplaza", "plazaname", "plaza", "tollname");
    private static final Set<String> LATITUDE_ALIASES = Set.of("latitude", "lat");
    private static final Set<String> LONGITUDE_ALIASES = Set.of("longitude", "lon", "lng", "long");

    public record ParseResult(List<TollPlaza> plazas, int skippedRows) {
    }

    public ParseResult parse(Reader reader) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        try (CSVParser parser = format.parse(reader)) {
            Map<String, String> headersByNormalizedName = new LinkedHashMap<>();
            for (String header : parser.getHeaderNames()) {
                headersByNormalizedName.putIfAbsent(normalize(header), header);
            }
            String nameColumn = findColumn(headersByNormalizedName, NAME_ALIASES);
            String latitudeColumn = findColumn(headersByNormalizedName, LATITUDE_ALIASES);
            String longitudeColumn = findColumn(headersByNormalizedName, LONGITUDE_ALIASES);
            if (nameColumn == null || latitudeColumn == null || longitudeColumn == null) {
                throw new IllegalArgumentException(
                        "Toll plaza CSV needs name, latitude and longitude columns; found: " + parser.getHeaderNames());
            }

            // Keyed by id so duplicate rows collapse before they reach the database.
            Map<String, TollPlaza> unique = new LinkedHashMap<>();
            int skipped = 0;
            for (CSVRecord row : parser) {
                TollPlaza plaza = toPlaza(row, nameColumn, latitudeColumn, longitudeColumn);
                if (plaza == null) {
                    skipped++;
                } else {
                    unique.putIfAbsent(plaza.getId(), plaza);
                }
            }
            return new ParseResult(List.copyOf(unique.values()), skipped);
        }
    }

    private TollPlaza toPlaza(CSVRecord row, String nameColumn, String latitudeColumn, String longitudeColumn) {
        if (!row.isSet(nameColumn) || !row.isSet(latitudeColumn) || !row.isSet(longitudeColumn)) {
            return null;
        }
        String name = row.get(nameColumn);
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            double latitude = Double.parseDouble(row.get(latitudeColumn));
            double longitude = Double.parseDouble(row.get(longitudeColumn));
            boolean valid = Double.isFinite(latitude) && Double.isFinite(longitude)
                    && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
            return valid ? new TollPlaza(name.trim(), latitude, longitude) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String findColumn(Map<String, String> headersByNormalizedName, Set<String> aliases) {
        for (String alias : aliases) {
            String header = headersByNormalizedName.get(alias);
            if (header != null) {
                return header;
            }
        }
        return null;
    }

    private String normalize(String header) {
        return header == null ? "" : header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
