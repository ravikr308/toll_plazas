package com.tollplaza.model;

import com.tollplaza.domain.TollPlaza;

/** A toll plaza found on a route, with its distance along the route from the source. */
public record TollMatch(TollPlaza plaza, double distanceFromSourceKm) {
}
