package com.tollplaza.csv;

import com.tollplaza.config.TollPlazaProperties;
import com.tollplaza.repository.TollPlazaUpsertRepository;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/** Loads the toll plaza CSV into the database at startup, as an idempotent upsert. */
@Component
public class TollPlazaCsvLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TollPlazaCsvLoader.class);
    private static final int BATCH_SIZE = 500;

    private final ResourceLoader resourceLoader;
    private final TollPlazaProperties properties;
    private final TollPlazaCsvParser parser;
    private final TollPlazaUpsertRepository upsertRepository;

    public TollPlazaCsvLoader(ResourceLoader resourceLoader, TollPlazaProperties properties,
                              TollPlazaCsvParser parser, TollPlazaUpsertRepository upsertRepository) {
        this.resourceLoader = resourceLoader;
        this.properties = properties;
        this.parser = parser;
        this.upsertRepository = upsertRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        load();
    }

    /** @return number of rows upserted */
    public int load() {
        Resource resource = resourceLoader.getResource(properties.csv().path());
        if (!resource.exists()) {
            log.warn("Toll plaza CSV not found at '{}'. The API will return no toll plazas until it is added.",
                    properties.csv().path());
            return 0;
        }
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            TollPlazaCsvParser.ParseResult result = parser.parse(reader);
            int upserted = upsertRepository.upsertAll(result.plazas(), BATCH_SIZE);
            log.info("Toll plaza import finished: {} upserted, {} rows skipped", upserted, result.skippedRows());
            return upserted;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read toll plaza CSV " + properties.csv().path(), e);
        }
    }
}
