package com.tollplaza;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tollplaza.client.GeocodingClient;
import com.tollplaza.client.RoutingClient;
import com.tollplaza.csv.TollPlazaCsvLoader;
import com.tollplaza.exception.InvalidPincodeException;
import com.tollplaza.model.GeoPoint;
import com.tollplaza.model.Route;
import com.tollplaza.repository.TollPlazaRepository;
import com.tollplaza.support.TestData;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full application context (controller, service, cache, H2, CSV import) with only the two
 * external providers mocked, so no network access is needed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "tollplaza.csv.path=classpath:test-data/toll_plazas.csv")
class TollPlazaApiIntegrationTest {

    private static final String URL = "/api/v1/toll-plazas";
    private static final GeoPoint SOURCE = new GeoPoint(28.0, 77.0);
    private static final GeoPoint DESTINATION = new GeoPoint(29.0, 77.0);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private TollPlazaRepository repository;
    @Autowired
    private TollPlazaCsvLoader loader;

    @MockBean
    private GeocodingClient geocodingClient;
    @MockBean
    private RoutingClient routingClient;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void csvIsImportedAtStartupAndReimportingIsIdempotent() {
        assertThat(repository.count()).isEqualTo(5);

        loader.load();

        assertThat(repository.count()).isEqualTo(5);
    }

    @Test
    void findsTollPlazasAlongTheRouteInOrder() throws Exception {
        stubNorthboundRoute();

        postRoute("110001", "560001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route.sourcePincode").value("110001"))
                .andExpect(jsonPath("$.route.destinationPincode").value("560001"))
                .andExpect(jsonPath("$.route.distanceInKm").value(111.2))
                .andExpect(jsonPath("$.tollPlazas.length()").value(2))
                .andExpect(jsonPath("$.tollPlazas[0].name").value("Early Toll"))
                .andExpect(jsonPath("$.tollPlazas[0].distanceFromSource", closeTo(11.12, 0.05)))
                .andExpect(jsonPath("$.tollPlazas[1].name").value("Near Toll"))
                .andExpect(jsonPath("$.tollPlazas[1].latitude").value(28.5))
                .andExpect(jsonPath("$.tollPlazas[1].longitude").value(77.001))
                .andExpect(jsonPath("$.tollPlazas[1].distanceFromSource", closeTo(55.6, 0.05)));
    }

    @Test
    void repeatedCallsAreServedFromTheCache() throws Exception {
        stubNorthboundRoute();

        postRoute("110001", "560001").andExpect(status().isOk());
        postRoute("110001", "560001").andExpect(status().isOk());
        postRoute("110001", "560001").andExpect(status().isOk());

        verify(routingClient, times(1)).getRoute(SOURCE, DESTINATION);
        verify(geocodingClient, times(1)).geocode("110001");
        verify(geocodingClient, times(1)).geocode("560001");
    }

    @Test
    void theReverseDirectionIsComputedSeparatelyBecauseDistancesAreFromTheSource() throws Exception {
        stubNorthboundRoute();
        List<GeoPoint> southbound = TestData.northboundPath().reversed();
        when(routingClient.getRoute(DESTINATION, SOURCE)).thenReturn(new Route(111.2, southbound));

        postRoute("110001", "560001").andExpect(status().isOk());
        postRoute("560001", "110001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tollPlazas[0].name").value("Near Toll"))
                .andExpect(jsonPath("$.tollPlazas[1].name").value("Early Toll"));

        verify(routingClient, times(1)).getRoute(SOURCE, DESTINATION);
        verify(routingClient, times(1)).getRoute(DESTINATION, SOURCE);
    }

    @Test
    void returnsAnEmptyListWhenNoPlazaIsOnTheRoute() throws Exception {
        GeoPoint a = new GeoPoint(10.0, 70.0);
        GeoPoint b = new GeoPoint(10.5, 70.0);
        when(geocodingClient.geocode("110001")).thenReturn(a);
        when(geocodingClient.geocode("560001")).thenReturn(b);
        when(routingClient.getRoute(a, b)).thenReturn(new Route(55.6, List.of(a, b)));

        postRoute("110001", "560001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route.distanceInKm").value(55.6))
                .andExpect(jsonPath("$.tollPlazas").isEmpty());
    }

    @Test
    void rejectsIdenticalPincodesWithoutTouchingTheProviders() throws Exception {
        postRoute("110001", "110001")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Source and destination pincodes cannot be the same"));

        verifyNoInteractions(geocodingClient, routingClient);
    }

    @Test
    void reportsAnUnknownPincodeAndDoesNotCacheTheFailure() throws Exception {
        when(geocodingClient.geocode("999999")).thenThrow(new InvalidPincodeException());

        postRoute("999999", "560001")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid source or destination pincode"));
        postRoute("999999", "560001").andExpect(status().isBadRequest());

        verify(geocodingClient, times(2)).geocode("999999");
        verify(routingClient, times(0)).getRoute(any(), any());
    }

    private org.springframework.test.web.servlet.ResultActions postRoute(String source, String destination)
            throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourcePincode\":\"" + source + "\",\"destinationPincode\":\"" + destination + "\"}"));
    }

    private void stubNorthboundRoute() {
        when(geocodingClient.geocode("110001")).thenReturn(SOURCE);
        when(geocodingClient.geocode("560001")).thenReturn(DESTINATION);
        when(routingClient.getRoute(SOURCE, DESTINATION)).thenReturn(new Route(111.2, TestData.northboundPath()));
    }
}
