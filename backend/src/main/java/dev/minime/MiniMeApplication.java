package dev.minime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // Phase 2: reminder scheduler
public class MiniMeApplication {
    public static void main(String[] args) {
        SpringApplication.run(MiniMeApplication.class, args);
    }
}
