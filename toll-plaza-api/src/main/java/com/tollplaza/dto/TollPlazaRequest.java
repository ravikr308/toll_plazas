package com.tollplaza.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TollPlazaRequest(
        @Schema(example = "110001")
        @NotBlank(message = "Source pincode is required")
        @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Invalid source or destination pincode")
        String sourcePincode,

        @Schema(example = "560001")
        @NotBlank(message = "Destination pincode is required")
        @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Invalid source or destination pincode")
        String destinationPincode) {

    /** Surrounding whitespace is not significant, so it is trimmed before validation and cache-key use. */
    public TollPlazaRequest {
        sourcePincode = sourcePincode == null ? null : sourcePincode.trim();
        destinationPincode = destinationPincode == null ? null : destinationPincode.trim();
    }
}
