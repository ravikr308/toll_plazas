package com.tollplaza.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tollplaza.client.GeocodingClient;
import com.tollplaza.client.RoutingClient;
import com.tollplaza.domain.TollPlaza;
import com.tollplaza.dto.TollPlazaRequest;
import com.tollplaza.dto.TollPlazaResponse;
import com.tollplaza.exception.ExternalServiceException;
import com.tollplaza.exception.InvalidPincodeException;
import com.tollplaza.exception.RouteNotFoundException;
import com.tollplaza.exception.SamePincodeException;
import com.tollplaza.matcher.PolylineTollMatcher;
import com.tollplaza.model.GeoPoint;
import com.tollplaza.model.Route;
import com.tollplaza.repository.TollPlazaRepository;
import com.tollplaza.support.TestData;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TollPlazaServiceTest {

    private static final GeoPoint SOURCE = new GeoPoint(28.0, 77.0);
    private static final GeoPoint DESTINATION = new GeoPoint(29.0, 77.0);

    @Mock
    private GeocodingClient geocodingClient;
    @Mock
    private RoutingClient routingClient;
    @Mock
    private TollPlazaRepository repository;

    private TollPlazaService service;

    @BeforeEach
    void setUp() {
        service = new TollPlazaService(geocodingClient, routingClient, repository,
                new PolylineTollMatcher(), TestData.properties());
    }

    @Test
    void rejectsIdenticalPincodesWithoutCallingAnyDependency() {
        assertThatThrownBy(() -> service.findTollPlazas(new TollPlazaRequest("110001", "110001")))
                .isInstanceOf(SamePincodeException.class)
                .hasMessage("Source and destination pincodes cannot be the same");

        verifyNoInteractions(geocodingClient, routingClient, repository);
    }

    @Test
    void returnsMatchingPlazasInRouteOrderWithRoundedDistances() {
        stubRoute();
        when(repository.findByLatitudeBetweenAndLongitudeBetween(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(
                        new TollPlaza("Near Toll", 28.5, 77.001),
                        new TollPlaza("Far Toll", 28.5, 77.1),
                        new TollPlaza("Early Toll", 28.1, 77.0)));

        TollPlazaResponse response = service.findTollPlazas(new TollPlazaRequest("110001", "560001"));

        assertThat(response.route().sourcePincode()).isEqualTo("110001");
        assertThat(response.route().destinationPincode()).isEqualTo("560001");
        assertThat(response.route().distanceInKm()).isEqualTo(111.2);
        assertThat(response.tollPlazas()).hasSize(2);
        assertThat(response.tollPlazas().get(0).name()).isEqualTo("Early Toll");
        assertThat(response.tollPlazas().get(0).latitude()).isEqualTo(28.1);
        assertThat(response.tollPlazas().get(0).longitude()).isEqualTo(77.0);
        assertThat(response.tollPlazas().get(0).distanceFromSource()).isCloseTo(11.12, within(0.001));
        assertThat(response.tollPlazas().get(1).name()).isEqualTo("Near Toll");
        assertThat(response.tollPlazas().get(1).distanceFromSource()).isCloseTo(55.6, within(0.05));
    }

    @Test
    void returnsAnEmptyListWhenNoPlazaIsOnTheRoute() {
        stubRoute();
        when(repository.findByLatitudeBetweenAndLongitudeBetween(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of());

        TollPlazaResponse response = service.findTollPlazas(new TollPlazaRequest("110001", "560001"));

        assertThat(response.tollPlazas()).isEmpty();
        assertThat(response.route().distanceInKm()).isEqualTo(111.2);
    }

    @Test
    void queriesTheDatabaseWithTheRouteBoundingBoxPlusMargin() {
        stubRoute();
        when(repository.findByLatitudeBetweenAndLongitudeBetween(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenAnswer(invocation -> {
                    double minLat = invocation.getArgument(0);
                    double maxLat = invocation.getArgument(1);
                    double minLon = invocation.getArgument(2);
                    double maxLon = invocation.getArgument(3);
                    assertThat(minLat).isLessThan(28.0).isGreaterThan(27.99);
                    assertThat(maxLat).isGreaterThan(29.0).isLessThan(29.01);
                    assertThat(minLon).isLessThan(77.0).isGreaterThan(76.99);
                    assertThat(maxLon).isGreaterThan(77.0).isLessThan(77.01);
                    return List.of();
                });

        service.findTollPlazas(new TollPlazaRequest("110001", "560001"));
    }

    @Test
    void propagatesAnInvalidPincodeFromTheGeocoder() {
        when(geocodingClient.geocode("999999")).thenThrow(new InvalidPincodeException());

        assertThatThrownBy(() -> service.findTollPlazas(new TollPlazaRequest("999999", "560001")))
                .isInstanceOf(InvalidPincodeException.class)
                .hasMessage("Invalid source or destination pincode");
        verifyNoInteractions(routingClient, repository);
    }

    @Test
    void propagatesRoutingFailures() {
        when(geocodingClient.geocode("110001")).thenReturn(SOURCE);
        when(geocodingClient.geocode("560001")).thenReturn(DESTINATION);
        when(routingClient.getRoute(SOURCE, DESTINATION)).thenThrow(new RouteNotFoundException());

        assertThatThrownBy(() -> service.findTollPlazas(new TollPlazaRequest("110001", "560001")))
                .isInstanceOf(RouteNotFoundException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void propagatesUpstreamOutages() {
        when(geocodingClient.geocode("110001")).thenThrow(new ExternalServiceException("down", null));

        assertThatThrownBy(() -> service.findTollPlazas(new TollPlazaRequest("110001", "560001")))
                .isInstanceOf(ExternalServiceException.class);
    }

    private void stubRoute() {
        when(geocodingClient.geocode("110001")).thenReturn(SOURCE);
        when(geocodingClient.geocode("560001")).thenReturn(DESTINATION);
        when(routingClient.getRoute(SOURCE, DESTINATION)).thenReturn(new Route(111.2, TestData.northboundPath()));
    }
}
