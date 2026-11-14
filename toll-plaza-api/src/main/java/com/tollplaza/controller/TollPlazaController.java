package com.tollplaza.controller;

import com.tollplaza.dto.ErrorResponse;
import com.tollplaza.dto.TollPlazaRequest;
import com.tollplaza.dto.TollPlazaResponse;
import com.tollplaza.service.TollPlazaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/toll-plazas")
@Tag(name = "Toll plazas")
public class TollPlazaController {

    private final TollPlazaService service;

    public TollPlazaController(TollPlazaService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List toll plazas between two pincodes, ordered by distance from the source")
    @ApiResponse(responseCode = "200", description = "Route summary and toll plazas (list is empty when there are none)")
    @ApiResponse(responseCode = "400", description = "Missing/invalid pincode, or source equals destination",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "No driving route between the pincodes",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "502", description = "Geocoding or routing provider failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TollPlazaResponse getTollPlazas(@Valid @RequestBody TollPlazaRequest request) {
        return service.findTollPlazas(request);
    }
}
