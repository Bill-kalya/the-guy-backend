package com.theguy.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class TheGuyApplication {
    public static void main(String[] args) {
        SpringApplication.run(TheGuyApplication.class, args);
        System.out.println("🚀 THE GUY BACKEND IS LIVE!");
        System.out.println("📍 Matching engine ready");
        System.out.println("🔌 WebSocket enabled");
        System.out.println("💸 Pricing engine active");
    }
}