package com.vplmqa.e2e;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.vplmqa.e2e"})
public class E2eServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(E2eServiceApplication.class, args);
    }
}
