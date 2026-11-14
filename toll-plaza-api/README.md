# Toll Plaza API

Spring Boot (Java 21) service that returns the toll plazas on the driving route between two Indian pincodes, with each plaza's coordinates and its distance from the source.

## How it works

1. Both pincodes are geocoded to coordinates (OpenStreetMap **Nominatim**, no API key).
2. The driving route polyline is fetched from **OSRM** (public demo server, no API key).
3. Toll plazas come from a CSV that is **upserted into an H2 database at startup**. A bounding-box query narrows the table to plazas near the route.
4. A plaza counts as "on the route" when it lies within `tollplaza.matching.threshold-km` (default 0.5 km) of the route polyline. Its `distanceFromSource` is the length of the route up to that point.
5. Results are cached per `(source, destination)` pair (Caffeine, 24 h, 1000 entries); geocoding results are cached too.

Both external providers sit behind interfaces (`GeocodingClient`, `RoutingClient`), so Google Maps, Mappls or a self-hosted OSRM can replace them without touching the service.

## Run it

Requirements: JDK 21+, Maven 3.9+, outbound internet access to `nominatim.openstreetmap.org` and `router.project-osrm.org`.

**1. Add the toll plaza CSV.** Download it from the Dropbox folder in the assignment and save it as:

```
src/main/resources/data/toll_plazas.csv
```

The file shipped in the repo contains only a header row. The loader finds the columns by header name, ignoring case, spaces and underscores. Accepted names:

| Field     | Accepted headers                                              |
|-----------|---------------------------------------------------------------|
| name      | `name`, `toll plaza name`, `toll_name`, `plaza`, `toll plaza` |
| latitude  | `latitude`, `lat`                                             |
| longitude | `longitude`, `lon`, `lng`, `long`                             |

If the real file uses other headers, add them to the alias sets in `TollPlazaCsvParser`. To load a file from elsewhere, set `tollplaza.csv.path=file:/path/to/file.csv`.

**2. Set a contact in the User-Agent** (Nominatim's usage policy asks for one) in `application.yml`:

```yaml
tollplaza.geocoding.user-agent: toll-plaza-api/1.0 (you@example.com)
```

**3. Start it:**

```bash
mvn spring-boot:run
```

The API listens on `http://localhost:8080`. Swagger UI: `http://localhost:8080/swagger-ui.html`.

## API

### `POST /api/v1/toll-plazas`

```bash
curl -X POST http://localhost:8080/api/v1/toll-plazas \
  -H "Content-Type: application/json" \
  -d '{"sourcePincode":"560064","destinationPincode":"411045"}'
```

Success (`200`):

```json
{
  "route": { "sourcePincode": "560064", "destinationPincode": "411045", "distanceInKm": 851.8 },
  "tollPlazas": [
    { "name": "Toll Plaza 1", "latitude": 13.1, "longitude": 77.6, "distanceFromSource": 25.4 }
  ]
}
```

`tollPlazas` is ordered by `distanceFromSource` and is `[]` when there are none. Distances are in km, rounded to 2 decimals.

| Status | When | Body |
|--------|------|------|
| 400 | Pincode missing/blank | `{"error":"Source pincode is required"}` (or destination) |
| 400 | Not a 6-digit pincode (first digit 1-9), or the geocoder cannot find it | `{"error":"Invalid source or destination pincode"}` |
| 400 | Source equals destination | `{"error":"Source and destination pincodes cannot be the same"}` |
| 400 | Malformed or missing JSON | `{"error":"Malformed or missing JSON request body"}` |
| 415 | Content-Type is not JSON | `{"error":"Content-Type must be application/json"}` |
| 422 | No driving route exists | `{"error":"No driving route found between the given pincodes"}` |
| 502 | Geocoder or router failed or timed out | `{"error":"... unavailable, please retry later"}` |
| 500 | Anything unexpected (details are logged, not returned) | `{"error":"Internal server error"}` |

A Postman collection is in `docs/toll-plaza-api.postman_collection.json`.

## Tests

```bash
mvn test
```

The tests need no network: geocoding and routing are mocked in the integration test and stubbed with `MockRestServiceServer` in the client tests. A JaCoCo coverage report is written to `target/site/jacoco/index.html`.

| Test class | Covers |
|------------|--------|
| `PolylineTollMatcherTest` | on/off route, ordering, threshold, bent routes, empty and degenerate input |
| `TollPlazaCsvParserTest` | header aliases, BOM, bad rows, duplicates, missing columns |
| `TollPlazaRepositoryTest` | batch upsert (insert, idempotent, update), bounding-box query |
| `NominatimGeocodingClientTest`, `OsrmRoutingClientTest` | response parsing, no route, provider errors |
| `TollPlazaServiceTest` | same pincode, no tolls, ordering/rounding, error propagation |
| `TollPlazaControllerTest` | JSON shape, validation messages, every error status |
| `TollPlazaApiIntegrationTest` | full context: CSV import, caching, reverse direction, failures not cached |

## Design notes and limits

- **Upsert:** the CSV is written with one JDBC batch of `MERGE ... KEY(id)`. The row id is a hash of lower-cased name plus coordinates (5 decimals), so re-importing the same file never duplicates rows. A plaza whose coordinates change in a later file is stored as a new row.
- **Accuracy vs. Mappls:** the assignment expects roughly 80-90% overlap with Mappls. Overlap depends on the CSV and the route provider. Tune `tollplaza.matching.threshold-km` if plazas are missed or extra ones appear. Public OSRM may choose a different road than Mappls.
- **Pincode location:** Nominatim returns one point per pincode, so the route starts and ends at that point, not at your exact address.
- **Public servers:** the Nominatim and OSRM demo servers are rate limited and not for heavy production use. Geocoding calls are spaced 1.1 s apart (`tollplaza.geocoding.min-interval`). Point `tollplaza.routing.base-url` at your own OSRM for real traffic.
- **Persistence:** H2 is in-memory and reloaded from the CSV on every start. For a file database set `spring.datasource.url=jdbc:h2:file:./data/tollplaza;DB_CLOSE_DELAY=-1`.

## Project layout

```
com.tollplaza
├── controller   REST endpoint
├── service      orchestration + caching
├── client       GeocodingClient / RoutingClient and their Nominatim / OSRM implementations
├── matcher      TollMatcher strategy and the polyline implementation
├── csv          CSV parser and startup loader
├── repository   JPA repository + JDBC batch upsert
├── domain, dto, model, geo, config, exception
```
