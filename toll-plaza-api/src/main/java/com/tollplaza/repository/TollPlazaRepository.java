package com.tollplaza.repository;

import com.tollplaza.domain.TollPlaza;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TollPlazaRepository extends JpaRepository<TollPlaza, String> {

    /** Candidate lookup: every plaza inside a latitude/longitude bounding box. */
    List<TollPlaza> findByLatitudeBetweenAndLongitudeBetween(
            double minLatitude, double maxLatitude, double minLongitude, double maxLongitude);
}
