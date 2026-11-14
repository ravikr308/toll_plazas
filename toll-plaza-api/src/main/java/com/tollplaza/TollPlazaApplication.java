package com.tollplaza;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TollPlazaApplication {

    public static void main(String[] args) {
        SpringApplication.run(TollPlazaApplication.class, args);
    }
}
