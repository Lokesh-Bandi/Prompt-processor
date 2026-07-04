package com.example.promptprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PromptProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(PromptProcessorApplication.class, args);
    }
}
