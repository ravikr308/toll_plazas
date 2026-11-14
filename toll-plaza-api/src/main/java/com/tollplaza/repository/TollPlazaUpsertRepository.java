package com.tollplaza.repository;

import com.tollplaza.domain.TollPlaza;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bulk upsert using a single JDBC batch of {@code MERGE ... KEY(id)} statements,
 * instead of a select-then-insert/update round trip per row.
 */
@Repository
public class TollPlazaUpsertRepository {

    private static final String UPSERT_SQL =
            "MERGE INTO toll_plaza (id, name, latitude, longitude) KEY (id) VALUES (?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    public TollPlazaUpsertRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public int upsertAll(Collection<TollPlaza> plazas, int batchSize) {
        List<TollPlaza> rows = new ArrayList<>(plazas);
        if (rows.isEmpty()) {
            return 0;
        }
        jdbcTemplate.batchUpdate(UPSERT_SQL, rows, batchSize, (ps, plaza) -> {
            ps.setString(1, plaza.getId());
            ps.setString(2, plaza.getName());
            ps.setDouble(3, plaza.getLatitude());
            ps.setDouble(4, plaza.getLongitude());
        });
        return rows.size();
    }
}
