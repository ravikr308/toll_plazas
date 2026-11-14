package com.tollplaza.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.tollplaza.domain.TollPlaza;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(TollPlazaUpsertRepository.class)
class TollPlazaRepositoryTest {

    @Autowired
    private TollPlazaRepository repository;

    @Autowired
    private TollPlazaUpsertRepository upsertRepository;

    @Test
    void upsertInsertsNewRows() {
        int count = upsertRepository.upsertAll(
                List.of(new TollPlaza("Alpha", 28.5, 77.0), new TollPlaza("Beta", 19.0, 72.8)), 10);

        assertThat(count).isEqualTo(2);
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void upsertingTheSameRowsTwiceDoesNotCreateDuplicates() {
        List<TollPlaza> rows = List.of(new TollPlaza("Alpha", 28.5, 77.0), new TollPlaza("Beta", 19.0, 72.8));

        upsertRepository.upsertAll(rows, 10);
        upsertRepository.upsertAll(rows, 10);

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void upsertUpdatesAnExistingRowInsteadOfInsertingANewOne() {
        upsertRepository.upsertAll(List.of(new TollPlaza("Alpha Plaza", 28.5, 77.0)), 10);

        // Same identity (name is compared case-insensitively), different spelling.
        upsertRepository.upsertAll(List.of(new TollPlaza("ALPHA PLAZA", 28.5, 77.0)), 10);

        List<TollPlaza> all = repository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getName()).isEqualTo("ALPHA PLAZA");
    }

    @Test
    void upsertWithNothingToDoIsANoOp() {
        assertThat(upsertRepository.upsertAll(List.of(), 10)).isZero();
        assertThat(repository.count()).isZero();
    }

    @Test
    void upsertHandlesMoreRowsThanOneBatch() {
        List<TollPlaza> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) {
            rows.add(new TollPlaza("Plaza " + i, 20.0 + i * 0.01, 75.0));
        }

        upsertRepository.upsertAll(rows, 10);

        assertThat(repository.count()).isEqualTo(25);
    }

    @Test
    void boundingBoxQueryReturnsOnlyPlazasInsideTheBox() {
        upsertRepository.upsertAll(List.of(
                new TollPlaza("Inside", 28.5, 77.0),
                new TollPlaza("North Of Box", 30.0, 77.0),
                new TollPlaza("East Of Box", 28.5, 79.0)), 10);

        List<TollPlaza> found = repository.findByLatitudeBetweenAndLongitudeBetween(28.0, 29.0, 76.5, 77.5);

        assertThat(found).extracting(TollPlaza::getName).containsExactly("Inside");
    }
}
