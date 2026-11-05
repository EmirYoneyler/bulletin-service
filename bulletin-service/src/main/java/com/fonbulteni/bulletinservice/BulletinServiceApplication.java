package com.fonbulteni.bulletinservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// Entities and repositories live under this package, so Boot's defaults find them.
// Explicit @EnableJpaRepositories/@EntityScan here would break @WebMvcTest slices.
@SpringBootApplication
@ConfigurationPropertiesScan
public class BulletinServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BulletinServiceApplication.class, args);
    }
}
