package com.ynu.shoting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ShottingBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShottingBookingApplication.class, args);
    }
}
