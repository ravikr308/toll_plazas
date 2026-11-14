package com.tollplaza.dto;

import java.util.List;

public record TollPlazaResponse(RouteSummary route, List<TollPlazaDetail> tollPlazas) {
}
