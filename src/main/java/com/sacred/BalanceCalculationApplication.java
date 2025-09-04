package com.sacred;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableRetry
@EnableAspectJAutoProxy
@EnableScheduling
public class BalanceCalculationApplication {

    public static void main(String[] args) {
        SpringApplication.run(BalanceCalculationApplication.class, args);
    }

}
