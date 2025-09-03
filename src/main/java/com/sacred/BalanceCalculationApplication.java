package com.sacred;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class BalanceCalculationApplication {

    public static void main(String[] args) {
        SpringApplication.run(BalanceCalculationApplication.class, args);
    }

}
