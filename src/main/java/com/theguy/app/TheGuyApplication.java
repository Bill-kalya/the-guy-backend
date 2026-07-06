package com.theguy.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class TheGuyApplication {
    public static void main(String[] args) {
        SpringApplication.run(TheGuyApplication.class, args);
        log.info("THE GUY BACKEND IS LIVE!");
        log.info("Matching engine ready");
        log.info("WebSocket enabled");
        log.info("Pricing engine active");
    }
}
