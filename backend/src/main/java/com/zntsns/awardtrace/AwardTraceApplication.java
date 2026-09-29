package com.zntsns.awardtrace;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AwardTraceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AwardTraceApplication.class, args);
    }

    /** The one source of "now" for logic that depends on the date, so tests can fix it. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
