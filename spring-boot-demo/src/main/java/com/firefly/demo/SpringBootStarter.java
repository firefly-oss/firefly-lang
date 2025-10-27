package com.firefly.demo;

import org.springframework.boot.SpringApplication;

public class SpringBootStarter {
    public static void start(Class<?> applicationClass, String[] args) {
        SpringApplication.run(applicationClass, args);
    }
}
