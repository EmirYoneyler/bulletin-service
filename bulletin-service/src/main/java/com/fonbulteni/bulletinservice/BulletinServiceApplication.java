package com.fonbulteni.bulletinservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.fonbulteni.bulletinservice")
@EnableJpaRepositories(basePackages = "com.fonbulteni.bulletinservice.repository")
@EntityScan(basePackages = "com.fonbulteni.bulletinservice.entity")
public class BulletinServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BulletinServiceApplication.class, args);
    }
}
