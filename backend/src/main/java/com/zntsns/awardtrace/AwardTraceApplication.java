package com.zntsns.awardtrace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AwardTraceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AwardTraceApplication.class, args);
    }
}
