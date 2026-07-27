package com.nanobase.specai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SpecAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpecAiApplication.class, args);
    }
}
