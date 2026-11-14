package com.tollplaza.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tollplaza.dto.RouteSummary;
import com.tollplaza.dto.TollPlazaDetail;
import com.tollplaza.dto.TollPlazaRequest;
import com.tollplaza.dto.TollPlazaResponse;
import com.tollplaza.exception.ExternalServiceException;
import com.tollplaza.exception.InvalidPincodeException;
import com.tollplaza.exception.RouteNotFoundException;
import com.tollplaza.exception.SamePincodeException;
import com.tollplaza.service.TollPlazaService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TollPlazaController.class)
class TollPlazaControllerTest {

    private static final String URL = "/api/v1/toll-plazas";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TollPlazaService service;

    @Test
    void returnsRouteAndTollPlazasInTheDocumentedShape() throws Exception {
        when(service.findTollPlazas(new TollPlazaRequest("110001", "560001"))).thenReturn(
                new TollPlazaResponse(
                        new RouteSummary("110001", "560001", 2100.0),
                        List.of(new TollPlazaDetail("Toll Plaza 1", 28.7041, 77.1025, 200.0),
                                new TollPlazaDetail("Toll Plaza 2", 19.076, 72.8777, 1400.0))));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourcePincode\":\"110001\",\"destinationPincode\":\"560001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route.sourcePincode").value("110001"))
                .andExpect(jsonPath("$.route.destinationPincode").value("560001"))
                .andExpect(jsonPath("$.route.distanceInKm").value(2100.0))
                .andExpect(jsonPath("$.tollPlazas.length()").value(2))
                .andExpect(jsonPath("$.tollPlazas[0].name").value("Toll Plaza 1"))
                .andExpect(jsonPath("$.tollPlazas[0].latitude").value(28.7041))
                .andExpect(jsonPath("$.tollPlazas[0].longitude").value(77.1025))
                .andExpect(jsonPath("$.tollPlazas[0].distanceFromSource").value(200.0))
                .andExpect(jsonPath("$.tollPlazas[1].name").value("Toll Plaza 2"));
    }

    @Test
    void returnsAnEmptyListWhenThereAreNoTollPlazas() throws Exception {
        when(service.findTollPlazas(new TollPlazaRequest("110001", "560001"))).thenReturn(
                new TollPlazaResponse(new RouteSummary("110001", "560001", 2100.0), List.of()));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourcePincode\":\"110001\",\"destinationPincode\":\"560001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route.distanceInKm").value(2100.0))
                .andExpect(jsonPath("$.tollPlazas").isArray())
                .andExpect(jsonPath("$.tollPlazas").isEmpty());
    }

    @Test
    void trimsWhitespaceAroundPincodes() throws Exception {
        when(service.findTollPlazas(new TollPlazaRequest("110001", "560001"))).thenReturn(
                new TollPlazaResponse(new RouteSummary("110001", "560001", 10.0), List.of()));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourcePincode\":\" 110001 \",\"destinationPincode\":\"560001\"}"))
                .andExpect(status().isOk());

        verify(service).findTollPlazas(new TollPlazaRequest("110001", "560001"));
    }

    @Test
    void rejectsAMissingSourcePincode() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationPincode\":\"560001\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Source pincode is required"));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsABlankDestinationPincode() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourcePincode\":\"110001\",\"destinationPincode\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Destination pincode is required"));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsPincodesThatAreNotSixDigits() throws Exception {
        for (String bad : List.of("12345", "1234567", "abcdef", "012345", "11 0001")) {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sourcePincode\":\"" + bad + "\",\"destinationPincode\":\"560001\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Invalid source or destination pincode"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void rejectsAMalformedBody() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed or missing JSON request body"));
    }

    @Test
    void rejectsAMissingBody() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed or missing JSON request body"));
    }

    @Test
    void rejectsANonJsonContentType() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.TEXT_PLAIN).content("110001,560001"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("Content-Type must be application/json"));
    }

    @Test
    void mapsSamePincodeTo400() throws Exception {
        when(service.findTollPlazas(any())).thenThrow(new SamePincodeException());

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourcePincode\":\"110001\",\"destinationPincode\":\"110001\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Source and destination pincodes cannot be the same"));
    }

    @Test
    void mapsAnUnknownPincodeTo400() throws Exception {
        when(service.findTollPlazas(any())).thenThrow(new InvalidPincodeException());

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourcePincode\":\"999999\",\"destinationPincode\":\"560001\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid source or destination pincode"));
    }

    @Test
    void mapsAMissingRouteTo422() throws Exception {
        when(service.findTollPlazas(any())).thenThrow(new RouteNotFoundException());

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourcePincode\":\"682001\",\"destinationPincode\":\"744101\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("No driving route found between the given pincodes"));
    }

    @Test
    void mapsUpstreamFailureTo502WithoutLeakingDetails() throws Exception {
        when(service.findTollPlazas(any()))
                .thenThrow(new ExternalServiceException("Routing service is unavailable, please retry later",
                        new RuntimeException("connect timed out to 10.1.2.3")));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourcePincode\":\"110001\",\"destinationPincode\":\"560001\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Routing service is unavailable, please retry later"));
    }

    @Test
    void mapsUnexpectedErrorsTo500WithAGenericMessage() throws Exception {
        when(service.findTollPlazas(any())).thenThrow(new IllegalStateException("boom: secret detail"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourcePincode\":\"110001\",\"destinationPincode\":\"560001\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error"));
    }
}
