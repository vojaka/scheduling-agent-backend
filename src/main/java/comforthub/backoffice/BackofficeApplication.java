package com.comforthub.backoffice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackofficeApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackofficeApplication.class, args);
    }
}
