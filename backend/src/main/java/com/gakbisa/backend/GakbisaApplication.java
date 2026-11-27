package com.gakbisa.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GakbisaApplication {

    public static void main(String[] args) {
        SpringApplication.run(GakbisaApplication.class, args);
    }
}
